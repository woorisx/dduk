package com.dduk.service.inventory;

import com.dduk.dto.inventory.InventoryDashboardResponseDto;
import com.dduk.dto.inventory.RecentMovementDto;
import com.dduk.dto.inventory.WarehouseDistributionDto;
import com.dduk.entity.admin.TaskHistory;
import com.dduk.entity.admin.TaskHistoryStatus;
import com.dduk.entity.admin.TaskHistoryType;
import com.dduk.entity.inventory.Inventory;
import com.dduk.entity.inventory.MovementType;
import com.dduk.entity.inventory.StockMovement;
import com.dduk.repository.admin.TaskHistoryRepository;
import com.dduk.repository.inventory.InventoryRepository;
import com.dduk.repository.inventory.StockMovementRepository;
import com.dduk.repository.inventory.WarehouseTransferRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryQueryService {

    private static final String INVENTORY_SHORTAGE_ACTION = "check_inventory_shortage";
    private static final String DEFAULT_VENDOR_NAME = "아망티";

    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final WarehouseTransferRepository warehouseTransferRepository;
    private final TaskHistoryRepository taskHistoryRepository;
    private final ObjectMapper objectMapper;

    public List<Inventory> getInventories(Long warehouseId, Long itemId, Boolean lowStockOnly) {
        List<Inventory> results;
        if (warehouseId != null && itemId != null) {
            results = inventoryRepository.findByWarehouseIdAndItemIdWithFetch(warehouseId, itemId);
        } else if (warehouseId != null) {
            results = inventoryRepository.findByWarehouseIdWithFetch(warehouseId);
        } else if (itemId != null) {
            results = inventoryRepository.findByItemIdWithFetch(itemId);
        } else {
            results = inventoryRepository.findAllWithItemAndWarehouse();
        }

        if (Boolean.TRUE.equals(lowStockOnly)) {
            return results.stream()
                    .filter(i -> i.getCurrentStock() <= i.getSafetyStock())
                    .collect(Collectors.toList());
        }
        return results;
    }

    public List<Inventory> getReorderRecommendations() {
        return inventoryRepository.findItemsNeedingReorderWithFetch();
    }

    public InventoryDashboardResponseDto getDashboardStats() {
        Long totalQty = inventoryRepository.getTotalStockQuantity();
        BigDecimal totalVal = inventoryRepository.getTotalInventoryValue();
        Long lowStock = inventoryRepository.countLowStockItems();
        Long outboundVol = stockMovementRepository.getOutboundVolumeSince(LocalDateTime.now().minusDays(30));
        Long pendingTransfers = warehouseTransferRepository.countPendingTransfers();

        List<Object[]> distributionRaw = inventoryRepository.getStockDistributionByWarehouse();
        List<WarehouseDistributionDto> distribution = distributionRaw.stream()
                .map(row -> WarehouseDistributionDto.builder()
                        .warehouseName((String) row[0])
                        .totalStock(row[1] != null ? ((Number) row[1]).longValue() : 0L)
                        .totalValue(row[2] instanceof BigDecimal ? (BigDecimal) row[2] : (row[2] != null ? BigDecimal.valueOf(((Number) row[2]).doubleValue()) : BigDecimal.ZERO))
                        .build())
                .collect(Collectors.toList());

        List<StockMovement> movements = stockMovementRepository.findAllWithFetch();
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<RecentMovementDto> recentMovements = movements.stream()
                .filter(m -> m.getCreatedAt() != null && m.getCreatedAt().isAfter(thirtyDaysAgo))
                .map(m -> RecentMovementDto.builder()
                        .id(m.getId())
                        .createdAt(m.getCreatedAt())
                        .referenceNo(m.getReferenceNo())
                        .movementType(m.getMovementType())
                        .itemName(m.getItem().getName())
                        .quantity(m.getQuantity())
                        .warehouseName(m.getWarehouse().getWarehouseName())
                        .build())
                .collect(Collectors.toList());

        return InventoryDashboardResponseDto.builder()
                .totalQuantity(totalQty != null ? totalQty : 0L)
                .totalValue(totalVal != null ? totalVal : BigDecimal.ZERO)
                .lowStockCount(lowStock != null ? lowStock : 0L)
                .outboundVolume30Days(outboundVol != null ? outboundVol : 0L)
                .pendingTransferCount(pendingTransfers != null ? pendingTransfers : 0L)
                .warehouseDistribution(distribution)
                .recentMovements(recentMovements)
                .inventoryShortageRpa(buildInventoryShortageRpa())
                .build();
    }

    public List<StockMovement> getStockMovements(Long warehouseId, Long itemId, MovementType movementType) {
        if (warehouseId != null && itemId != null && movementType != null) {
            return stockMovementRepository.findByWarehouseIdAndItemIdWithFetch(warehouseId, itemId)
                    .stream().filter(m -> m.getMovementType() == movementType)
                    .collect(Collectors.toList());
        } else if (warehouseId != null && movementType != null) {
            return stockMovementRepository.findByWarehouseIdAndMovementTypeWithFetch(warehouseId, movementType);
        } else if (itemId != null && movementType != null) {
            return stockMovementRepository.findByItemIdAndMovementTypeWithFetch(itemId, movementType);
        } else if (warehouseId != null && itemId != null) {
            return stockMovementRepository.findByWarehouseIdAndItemIdWithFetch(warehouseId, itemId);
        } else if (warehouseId != null) {
            return stockMovementRepository.findByWarehouseIdWithFetch(warehouseId);
        } else if (itemId != null) {
            return stockMovementRepository.findByItemIdWithFetch(itemId);
        } else if (movementType != null) {
            return stockMovementRepository.findByMovementTypeWithFetch(movementType);
        } else {
            return stockMovementRepository.findAllWithFetch();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildInventoryShortageRpa() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskType", "INVENTORY_SHORTAGE");
        result.put("actionName", INVENTORY_SHORTAGE_ACTION);
        result.put("available", false);
        result.put("status", "EMPTY");
        result.put("vendorName", "내부 분석기");
        result.put("message", "아직 완료된 장기 체화 및 임박 재고 분석 결과가 없어.");
        result.put("latestTaskId", null);
        result.put("latestCollectedAt", null);
        result.put("agingCount", 0);
        result.put("expiryCount", 0);
        result.put("totalRiskCount", 0);
        result.put("items", List.of());

        Optional<TaskHistory> latestTask = taskHistoryRepository
                .findFirstByTaskTypeAndActionNameAndStatusOrderByCompletedAtDescIdDesc(
                        TaskHistoryType.RPA,
                        INVENTORY_SHORTAGE_ACTION,
                        TaskHistoryStatus.SUCCESS
                );

        if (latestTask.isEmpty()) {
            return result;
        }

        TaskHistory taskHistory = latestTask.get();
        result.put("latestTaskId", taskHistory.getTaskId());
        result.put("latestCollectedAt", taskHistory.getCompletedAt() == null ? null : taskHistory.getCompletedAt().toString());

        String sourceFilePath = readString(parseMap(taskHistory.getResponsePayload()), "data", "filePath");
        if (sourceFilePath == null || sourceFilePath.isBlank()) {
            result.put("status", "MISSING_FILE");
            result.put("message", "최근 분석 이력은 있지만 결과 파일 경로를 찾지 못했어.");
            return result;
        }

        Path resolvedPath = resolveProjectPath(sourceFilePath);
        if (resolvedPath == null || !Files.exists(resolvedPath)) {
            result.put("status", "MISSING_FILE");
            result.put("message", "최근 분석 이력은 있지만 결과 파일이 없어.");
            return result;
        }

        Map<String, Object> fileContent = readJsonFile(resolvedPath);
        if (fileContent.isEmpty()) {
            result.put("status", "EMPTY_RESULT");
            result.put("message", "최근 분석 파일은 있지만 분석 결과 내용을 해석하지 못했어.");
            return result;
        }

        Map<String, Object> summary = (Map<String, Object>) fileContent.get("summary");
        int agingCount = summary != null && summary.get("agingCount") != null ? ((Number) summary.get("agingCount")).intValue() : 0;
        int expiryCount = summary != null && summary.get("expiryCount") != null ? ((Number) summary.get("expiryCount")).intValue() : 0;
        int totalRiskCount = summary != null && summary.get("totalRiskCount") != null ? ((Number) summary.get("totalRiskCount")).intValue() : 0;
        boolean expirySupport = summary != null && Boolean.TRUE.equals(summary.get("expirySupport"));

        result.put("agingCount", agingCount);
        result.put("expiryCount", expiryCount);
        result.put("totalRiskCount", totalRiskCount);

        List<Map<String, Object>> items = (List<Map<String, Object>>) fileContent.get("items");
        if (items == null) {
            items = List.of();
        }
        result.put("items", items);

        result.put("available", true);
        if (totalRiskCount == 0) {
            result.put("status", "EMPTY_RESULT");
            result.put("message", "분석을 완료했지만 노출할 리스크 항목은 없었어.");
        } else {
            if (!expirySupport) {
                result.put("status", "ANALYSIS_PARTIAL");
                result.put("message", "장기 체화 재고 " + agingCount + "건을 탐지했고, 유통기한 데이터는 아직 연결되지 않았어.");
            } else {
                result.put("status", "READY");
                result.put("message", "장기 체화 재고 " + agingCount + "건, 유통기한 임박 재고 " + expiryCount + "건을 탐지했어.");
            }
        }

        return result;
    }

    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (IOException exception) {
            return Map.of();
        }
    }

    private String readString(Map<String, Object> root, String parentKey, String childKey) {
        Object parent = root.get(parentKey);
        if (parent instanceof Map<?, ?> nested) {
            Object value = nested.get(childKey);
            return value == null ? null : String.valueOf(value);
        }
        return null;
    }

    private Map<String, Object> readJsonFile(Path filePath) {
        try {
            return objectMapper.readValue(filePath.toFile(), new TypeReference<Map<String, Object>>() {});
        } catch (IOException exception) {
            return Map.of();
        }
    }

    private Path resolveProjectPath(String filePath) {
        Path rawPath = Paths.get(filePath);
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        Path projectRoot = "backend".equalsIgnoreCase(workingDir.getFileName().toString())
                ? workingDir.getParent()
                : workingDir;
        Path outputRoot = projectRoot.resolve("rpa").resolve("outputs").normalize();
        Path resolved = rawPath.isAbsolute()
                ? rawPath.normalize()
                : projectRoot.resolve(filePath).normalize();

        if (!resolved.startsWith(outputRoot)) {
            return null;
        }
        return resolved;
    }
}
