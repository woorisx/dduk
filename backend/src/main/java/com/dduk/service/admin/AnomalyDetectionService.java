package com.dduk.service.admin;

import com.dduk.dto.admin.AnomalyDetectionSummaryDto;
import com.dduk.dto.admin.AnomalyLogStatusUpdateDto;
import com.dduk.dto.admin.AnomalyRefreshResponseDto;
import com.dduk.dto.inventory.PurchaseRecommendationDto;
import com.dduk.entity.admin.AnomalyLog;
import com.dduk.entity.admin.AnomalyLogStatus;
import com.dduk.entity.inventory.Inventory;
import com.dduk.repository.admin.AnomalyLogRepository;
import com.dduk.repository.inventory.InventoryRepository;
import com.dduk.service.inventory.PurchaseRecommendationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnomalyDetectionService {

    private final AnomalyLogRepository anomalyLogRepository;
    private final InventoryRepository inventoryRepository;
    private final PurchaseRecommendationService purchaseRecommendationService;
    private final ObjectMapper objectMapper;

    public Page<AnomalyLog> getAnomalyLogs(
            String status,
            Boolean active,
            String severity,
            String ruleCode,
            String keyword,
            Pageable pageable
    ) {
        return anomalyLogRepository.search(
                normalizeStatus(status),
                active,
                normalizeBlank(severity),
                normalizeBlank(ruleCode),
                normalizeBlank(keyword),
                pageable
        );
    }

    public AnomalyDetectionSummaryDto getSummary() {
        Optional<AnomalyLog> latest = anomalyLogRepository.findFirstByActiveTrueOrderByLastDetectedAtDescIdDesc();
        return AnomalyDetectionSummaryDto.builder()
                .activeCount(anomalyLogRepository.countByActiveTrue())
                .openCount(anomalyLogRepository.countByActiveTrueAndStatus(AnomalyLogStatus.OPEN))
                .confirmedCount(anomalyLogRepository.countByActiveTrueAndStatus(AnomalyLogStatus.CONFIRMED))
                .criticalCount(anomalyLogRepository.countByActiveTrueAndSeverity("CRITICAL"))
                .highCount(anomalyLogRepository.countByActiveTrueAndSeverity("HIGH"))
                .latestDetectedAt(latest.map(AnomalyLog::getLastDetectedAt).map(LocalDateTime::toString).orElse(null))
                .build();
    }

    @Transactional
    public AnomalyRefreshResponseDto refreshAnomalies() {
        List<DetectedAnomaly> detected = detectAnomalies();
        Map<String, DetectedAnomaly> detectedByKey = detected.stream()
                .collect(Collectors.toMap(DetectedAnomaly::anomalyKey, Function.identity(), (left, right) -> left, LinkedHashMap::new));

        List<AnomalyLog> existingLogs = anomalyLogRepository.findByAnomalyKeyIn(detectedByKey.keySet());
        Map<String, AnomalyLog> existingByKey = existingLogs.stream()
                .collect(Collectors.toMap(AnomalyLog::getAnomalyKey, Function.identity()));

        LocalDateTime now = LocalDateTime.now();
        long activatedCount = 0L;

        for (DetectedAnomaly detectedAnomaly : detectedByKey.values()) {
            AnomalyLog anomalyLog = existingByKey.get(detectedAnomaly.anomalyKey());
            if (anomalyLog == null) {
                anomalyLog = AnomalyLog.builder()
                        .anomalyKey(detectedAnomaly.anomalyKey())
                        .status(AnomalyLogStatus.OPEN)
                        .firstDetectedAt(now)
                        .build();
            }

            if (!anomalyLog.isActive()) {
                activatedCount += 1;
            }

            anomalyLog.setRuleCode(detectedAnomaly.ruleCode());
            anomalyLog.setSeverity(detectedAnomaly.severity());
            anomalyLog.setTitle(detectedAnomaly.title());
            anomalyLog.setSummary(detectedAnomaly.summary());
            anomalyLog.setSourceType(detectedAnomaly.sourceType());
            anomalyLog.setSourceId(detectedAnomaly.sourceId());
            anomalyLog.setSourceLabel(detectedAnomaly.sourceLabel());
            anomalyLog.setPayloadJson(detectedAnomaly.payloadJson());
            anomalyLog.setActive(true);
            anomalyLog.setLastDetectedAt(now);

            anomalyLogRepository.save(anomalyLog);
        }

        long deactivatedCount = 0L;
        for (AnomalyLog anomalyLog : anomalyLogRepository.findAll()) {
            if (detectedByKey.containsKey(anomalyLog.getAnomalyKey())) {
                continue;
            }
            if (anomalyLog.isActive()) {
                anomalyLog.setActive(false);
                anomalyLogRepository.save(anomalyLog);
                deactivatedCount += 1;
            }
        }

        return AnomalyRefreshResponseDto.builder()
                .detectedCount(detectedByKey.size())
                .activatedCount(activatedCount)
                .deactivatedCount(deactivatedCount)
                .summary(getSummary())
                .build();
    }

    @Transactional
    public AnomalyLog updateStatus(Long anomalyId, AnomalyLogStatusUpdateDto request) {
        AnomalyLog anomalyLog = anomalyLogRepository.findById(anomalyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "이상 탐지 항목을 찾지 못했어."));

        anomalyLog.setStatus(normalizeStatus(request.getStatus()));
        anomalyLog.setReviewNote(trimToNull(request.getReviewNote()));
        anomalyLog.setReviewedBy(trimToNull(request.getReviewedBy()));
        anomalyLog.setReviewedAt(LocalDateTime.now());
        return anomalyLogRepository.save(anomalyLog);
    }

    private List<DetectedAnomaly> detectAnomalies() {
        List<Inventory> inventories = inventoryRepository.findAllWithItemAndWarehouse();
        Map<Long, PurchaseRecommendationDto> recommendationByInventoryId = purchaseRecommendationService.getRecommendations().stream()
                .collect(Collectors.toMap(PurchaseRecommendationDto::getInventoryId, Function.identity(), (left, right) -> left));

        List<DetectedAnomaly> detected = new ArrayList<>();
        for (Inventory inventory : inventories) {
            if (inventory.getCurrentStock() < 0) {
                detected.add(buildAnomaly(
                        "NEGATIVE_CURRENT_STOCK",
                        "CRITICAL",
                        inventory,
                        "현재고가 음수야",
                        "현재고가 0보다 작아서 즉시 재고 정합성 확인이 필요해.",
                        Map.of(
                                "currentStock", inventory.getCurrentStock(),
                                "safetyStock", inventory.getSafetyStock(),
                                "availableStock", inventory.getAvailableStock()
                        )
                ));
            }

            if (inventory.getAvailableStock() < 0) {
                detected.add(buildAnomaly(
                        "NEGATIVE_AVAILABLE_STOCK",
                        "CRITICAL",
                        inventory,
                        "가용재고가 음수야",
                        "예약 수량 때문에 가용재고가 음수로 내려가서 출고 또는 이동 정합성 확인이 필요해.",
                        Map.of(
                                "currentStock", inventory.getCurrentStock(),
                                "allocatedStock", inventory.getAllocatedStock(),
                                "availableStock", inventory.getAvailableStock()
                        )
                ));
            }

            if (inventory.getCurrentStock() <= 0 && inventory.getSafetyStock() > 0) {
                detected.add(buildAnomaly(
                        "OUT_OF_STOCK_WITH_SAFETY",
                        "HIGH",
                        inventory,
                        "안전재고가 있는 품목이 품절됐어",
                        "현재고가 0 이하인데 안전재고 기준이 남아 있어서 긴급 보충 검토가 필요해.",
                        Map.of(
                                "currentStock", inventory.getCurrentStock(),
                                "safetyStock", inventory.getSafetyStock(),
                                "availableStock", inventory.getAvailableStock()
                        )
                ));
            }

            PurchaseRecommendationDto recommendation = recommendationByInventoryId.get(inventory.getId());
            if (recommendation != null
                    && Set.of("CRITICAL", "HIGH").contains(recommendation.getUrgency())
                    && isBlank(recommendation.getDefaultVendorName())) {
                detected.add(buildAnomaly(
                        "REORDER_VENDOR_MISSING",
                        "HIGH",
                        inventory,
                        "긴급 발주 후보인데 기본 공급처가 없어",
                        "긴급 재발주가 필요하지만 기본 공급처가 비어 있어서 수동 공급처 확인이 먼저 필요해.",
                        Map.of(
                                "urgency", recommendation.getUrgency(),
                                "recommendedOrderQty", recommendation.getRecommendedOrderQty(),
                                "daysUntilStockout", recommendation.getDaysUntilStockout()
                        )
                ));
            }

            if (recommendation != null
                    && recommendation.getDaysUntilStockout() >= 0
                    && recommendation.getDaysUntilStockout() <= recommendation.getLeadTimeDays()) {
                detected.add(buildAnomaly(
                        "STOCKOUT_BEFORE_LEAD_TIME",
                        "MEDIUM",
                        inventory,
                        "리드타임보다 먼저 재고가 끊길 수 있어",
                        "예상 소진일이 기본 리드타임 안쪽이라 발주 또는 대체 계획을 앞당겨야 해.",
                        Map.of(
                                "leadTimeDays", recommendation.getLeadTimeDays(),
                                "daysUntilStockout", recommendation.getDaysUntilStockout(),
                                "recommendedOrderQty", recommendation.getRecommendedOrderQty()
                        )
                ));
            }
        }
        return detected;
    }

    private DetectedAnomaly buildAnomaly(
            String ruleCode,
            String severity,
            Inventory inventory,
            String title,
            String summary,
            Map<String, Object> payload
    ) {
        String sourceId = String.valueOf(inventory.getId());
        String sourceLabel = inventory.getItem().getName() + " / " + inventory.getWarehouse().getWarehouseName();
        Map<String, Object> fullPayload = new LinkedHashMap<>(payload);
        fullPayload.put("itemId", inventory.getItem().getId());
        fullPayload.put("itemCode", inventory.getItem().getItemCode());
        fullPayload.put("warehouseId", inventory.getWarehouse().getId());
        fullPayload.put("warehouseCode", inventory.getWarehouse().getWarehouseCode());
        fullPayload.put("sourceLabel", sourceLabel);

        return DetectedAnomaly.builder()
                .anomalyKey(ruleCode + ":" + sourceId)
                .ruleCode(ruleCode)
                .severity(severity)
                .title(title)
                .summary(summary)
                .sourceType("INVENTORY")
                .sourceId(sourceId)
                .sourceLabel(sourceLabel)
                .payloadJson(writePayload(fullPayload))
                .build();
    }

    private String writePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("이상 탐지 payload 직렬화에 실패했어.", exception);
        }
    }

    private AnomalyLogStatus normalizeStatus(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return AnomalyLogStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 anomaly status야: " + value);
        }
    }

    private String normalizeBlank(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String trimToNull(String value) {
        String normalized = normalizeBlank(value);
        return normalized == null ? null : normalized.substring(0, Math.min(normalized.length(), 500));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank() || "-".equals(value.trim());
    }

    @Builder
    private record DetectedAnomaly(
            String anomalyKey,
            String ruleCode,
            String severity,
            String title,
            String summary,
            String sourceType,
            String sourceId,
            String sourceLabel,
            String payloadJson
    ) {}
}
