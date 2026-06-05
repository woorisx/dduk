package com.dduk.service.inventory;

import com.dduk.entity.inventory.Inventory;
import com.dduk.repository.inventory.InventoryRepository;
import com.dduk.repository.inventory.StockMovementRepository;
import com.dduk.service.admin.taskhistory.TaskHistoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryAgingAnalysisService {

    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final TaskHistoryService taskHistoryService;
    private final ObjectMapper objectMapper;

    @Async
    @Transactional
    public void runAsync(String taskId, String taskType, String actionName) {
        log.info("[Inventory Aging] Starting inventory aging analysis task: {}", taskId);
        try {
            // 모의 비동기 실행 딜레이 1초 적용
            Thread.sleep(1000);

            List<Inventory> allInventories = inventoryRepository.findAllWithItemAndWarehouse();
            List<Map<String, Object>> riskItems = new ArrayList<>();
            LocalDateTime limitDate = LocalDateTime.now().minusDays(90);

            int agingCount = 0;

            for (Inventory inv : allInventories) {
                if (inv.getCurrentStock() <= 0) {
                    continue;
                }

                LocalDateTime lastOutbound = stockMovementRepository.findLastOutboundTime(inv.getItem().getId(), inv.getWarehouse().getId());
                boolean isAging = false;
                LocalDateTime baseDateForDays = null;

                if (lastOutbound != null) {
                    if (lastOutbound.isBefore(limitDate)) {
                        isAging = true;
                        baseDateForDays = lastOutbound;
                    }
                } else {
                    // Outbound 이력이 아예 없는 경우, updatedAt 기준으로 90일 이상 미활동 여부 판단
                    LocalDateTime lastUpdate = inv.getUpdatedAt();
                    if (lastUpdate != null && lastUpdate.isBefore(limitDate)) {
                        isAging = true;
                        baseDateForDays = lastUpdate;
                    }
                }

                if (isAging) {
                    agingCount++;
                    long daysWithoutOutbound = ChronoUnit.DAYS.between(baseDateForDays, LocalDateTime.now());

                    Map<String, Object> itemMap = new LinkedHashMap<>();
                    itemMap.put("itemId", inv.getItem().getId());
                    itemMap.put("itemName", inv.getItem().getName());
                    itemMap.put("warehouseName", inv.getWarehouse().getWarehouseName());
                    itemMap.put("availableStock", inv.getAvailableStock());
                    itemMap.put("statusType", "AGING");
                    itemMap.put("statusLabel", "장기 체화");
                    itemMap.put("lastMovementAt", baseDateForDays.toString());
                    itemMap.put("daysWithoutOutbound", daysWithoutOutbound);
                    itemMap.put("expiryDate", null);
                    itemMap.put("daysToExpiry", null);
                    
                    // 조치 제안
                    String action = "할인 판매 또는 우선 출고 검토";
                    if (daysWithoutOutbound > 120) {
                        action = "추가 구매 중단 및 긴급 특별 프로모션 진행";
                    } else if (daysWithoutOutbound > 100) {
                        action = "묶음 판매 상품 구성 및 행사 전환";
                    }
                    itemMap.put("recommendedAction", action);

                    riskItems.add(itemMap);
                }
            }

            // 알림 텍스트 프리뷰 작성
            StringBuilder slackPreview = new StringBuilder();
            slackPreview.append("[재고 리스크 주간 알림]\n");
            slackPreview.append("- 장기 체화: ").append(agingCount).append("건\n");
            slackPreview.append("- 유통기한 임박: 0건\n");
            if (!riskItems.isEmpty()) {
                slackPreview.append("- 우선 확인 품목: ");
                for (int i = 0; i < Math.min(riskItems.size(), 3); i++) {
                    slackPreview.append(riskItems.get(i).get("itemName"));
                    if (i < Math.min(riskItems.size(), 3) - 1) {
                        slackPreview.append(", ");
                    }
                }
                if (riskItems.size() > 3) {
                    slackPreview.append(" 등");
                }
            }

            String emailPreview = "제목: [DDUK ERP] 주간 재고 리스크 알림\n\n장기 체화 재고 " + agingCount + "건이 탐지되었습니다. 상세 내용은 재고 대시보드에서 확인해주시기 바랍니다.";

            // 전체 JSON 구조 작성
            Map<String, Object> outputMap = new LinkedHashMap<>();
            outputMap.put("taskId", taskId);
            outputMap.put("taskType", taskType);
            outputMap.put("actionName", actionName);
            outputMap.put("generatedAt", LocalDateTime.now().toString());

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("agingCount", agingCount);
            summary.put("expiryCount", 0);
            summary.put("totalRiskCount", agingCount);
            summary.put("expirySupport", false);
            outputMap.put("summary", summary);

            Map<String, Object> notifications = new LinkedHashMap<>();
            notifications.put("slackPreview", slackPreview.toString());
            notifications.put("emailPreview", emailPreview);
            outputMap.put("notifications", notifications);

            outputMap.put("items", riskItems);

            // 파일에 기록
            String relativePath = "rpa/outputs/inventory_aging_" + taskId + ".json";
            Path outputPath = resolveProjectPath(relativePath);
            
            Files.createDirectories(outputPath.getParent());
            objectMapper.writeValue(outputPath.toFile(), outputMap);

            log.info("[Inventory Aging] Analysis result saved to: {}", outputPath.toAbsolutePath());

            // Callback 데이터 생성
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("taskId", taskId);
            payload.put("taskType", taskType);
            payload.put("actionName", actionName);
            payload.put("status", "success");
            
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("filePath", relativePath);
            data.put("agingCount", agingCount);
            data.put("expiryCount", 0);
            data.put("totalRiskCount", agingCount);
            payload.put("data", data);

            taskHistoryService.saveRpaCallbackResult(taskId, actionName, "success", payload, null);
            log.info("[Inventory Aging] Analysis task successfully completed and task history updated. Task ID: {}", taskId);

        } catch (Exception e) {
            log.error("[Inventory Aging] Error during inventory aging analysis task {}", taskId, e);
            taskHistoryService.saveRpaCallbackResult(taskId, actionName, "failed", null, e.getMessage());
        }
    }

    private Path resolveProjectPath(String filePath) {
        Path rawPath = Paths.get(filePath);
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        Path projectRoot = "backend".equalsIgnoreCase(workingDir.getFileName().toString())
                ? workingDir.getParent()
                : workingDir;
        return rawPath.isAbsolute()
                ? rawPath.normalize()
                : projectRoot.resolve(filePath).normalize();
    }
}
