package com.dduk.service.inventory;

import com.dduk.dto.inventory.PurchaseRecommendationDto;
import com.dduk.entity.inventory.Inventory;
import com.dduk.entity.inventory.MovementType;
import com.dduk.entity.inventory.PurchaseOrderItem;
import com.dduk.entity.inventory.StockMovement;
import com.dduk.repository.inventory.InventoryRepository;
import com.dduk.repository.inventory.PurchaseOrderItemRepository;
import com.dduk.repository.inventory.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseRecommendationService {

    private static final int DEFAULT_LEAD_TIME_DAYS = 7;
    private static final int OUTBOUND_LOOKBACK_DAYS = 30;
    private static final int LEAD_TIME_LOOKBACK_DAYS = 180;
    private static final int MIN_OUTBOUND_HISTORY_DAYS = 3;
    private static final String STATUS_READY = "READY";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final String STATUS_REVIEW = "REVIEW";

    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;

    public List<PurchaseRecommendationDto> getRecommendations() {
        List<Inventory> allInventories = inventoryRepository.findAllWithItemAndWarehouse();
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(OUTBOUND_LOOKBACK_DAYS);

        List<StockMovement> recentMovements = stockMovementRepository.findAllWithFetch().stream()
                .filter(movement -> movement.getCreatedAt().isAfter(thirtyDaysAgo))
                .filter(movement -> movement.getMovementType() == MovementType.OUTBOUND
                        || movement.getMovementType() == MovementType.TRANSFER_OUT)
                .toList();

        Map<String, Long> outboundQuantityMap = recentMovements.stream()
                .collect(Collectors.groupingBy(
                        movement -> movement.getItem().getId() + "_" + movement.getWarehouse().getId(),
                        Collectors.summingLong(StockMovement::getQuantity)
                ));

        Map<String, Integer> outboundHistoryDaysMap = recentMovements.stream()
                .collect(Collectors.groupingBy(
                        movement -> movement.getItem().getId() + "_" + movement.getWarehouse().getId(),
                        Collectors.collectingAndThen(
                                Collectors.mapping(movement -> movement.getCreatedAt().toLocalDate(), Collectors.toSet()),
                                days -> days.size()
                        )
                ));

        Map<Long, LeadTimeStats> leadTimeStatsByItemId = buildLeadTimeStatsByItemId();
        List<PurchaseRecommendationDto> recommendations = new ArrayList<>();

        for (Inventory inventory : allInventories) {
            String key = inventory.getItem().getId() + "_" + inventory.getWarehouse().getId();
            long outboundLast30Days = outboundQuantityMap.getOrDefault(key, 0L);
            int outboundHistoryDays = outboundHistoryDaysMap.getOrDefault(key, 0);
            double avgDailyUsage = outboundLast30Days / (double) OUTBOUND_LOOKBACK_DAYS;
            double avgMonthlyUsage = outboundLast30Days;

            int currentStock = safeInt(inventory.getCurrentStock());
            int safetyStock = safeInt(inventory.getSafetyStock());
            int allocatedStock = safeInt(inventory.getAllocatedStock());
            int availableStock = safeInt(inventory.getAvailableStock());
            int reorderBasisStock = Math.min(currentStock, availableStock);

            LeadTimeStats leadTimeStats = leadTimeStatsByItemId.get(inventory.getItem().getId());
            int leadTimeDays = leadTimeStats != null ? leadTimeStats.averageLeadTimeDays() : DEFAULT_LEAD_TIME_DAYS;
            int rawRecommendedQty = (int) Math.ceil(avgDailyUsage * leadTimeDays) + safetyStock - reorderBasisStock;
            int recommendedQty = rawRecommendedQty <= 0
                    ? Math.max(safetyStock - reorderBasisStock, 1)
                    : rawRecommendedQty;

            boolean needsAttention = currentStock <= safetyStock
                    || availableStock <= safetyStock
                    || currentStock <= 0
                    || rawRecommendedQty > 0;
            if (!needsAttention) {
                continue;
            }

            RecommendationState state = decideState(
                    currentStock,
                    availableStock,
                    outboundLast30Days,
                    outboundHistoryDays,
                    leadTimeStats,
                    recommendedQty,
                    avgMonthlyUsage
            );

            recommendations.add(PurchaseRecommendationDto.builder()
                    .inventoryId(inventory.getId())
                    .itemId(inventory.getItem().getId())
                    .itemCode(inventory.getItem().getItemCode())
                    .itemName(inventory.getItem().getName())
                    .itemCategory(inventory.getItem().getCategory())
                    .unit(inventory.getItem().getUnit())
                    .warehouseId(inventory.getWarehouse().getId())
                    .warehouseName(inventory.getWarehouse().getWarehouseName())
                    .currentStock(currentStock)
                    .safetyStock(safetyStock)
                    .allocatedStock(allocatedStock)
                    .availableStock(availableStock)
                    .averageCost(inventory.getAverageCost())
                    .avgMonthlyUsage(avgMonthlyUsage)
                    .recommendedOrderQty(recommendedQty)
                    .urgency(calcUrgency(reorderBasisStock, safetyStock))
                    .daysUntilStockout(calculateDaysUntilStockout(reorderBasisStock, avgDailyUsage))
                    .defaultVendorName(resolveVendorName(inventory))
                    .leadTimeDays(leadTimeDays)
                    .recommendationStatus(state.status())
                    .orderable(STATUS_READY.equals(state.status()))
                    .statusReason(state.reason())
                    .reviewMessage(state.reviewMessage())
                    .outboundQuantityLast30Days(outboundLast30Days)
                    .outboundHistoryDays(outboundHistoryDays)
                    .recentPurchaseOrderCount(leadTimeStats != null ? leadTimeStats.purchaseOrderCount() : 0)
                    .leadTimeReliable(leadTimeStats != null)
                    .evidenceSummary(buildEvidenceSummary(outboundHistoryDays, outboundLast30Days, leadTimeStats))
                    .build());
        }

        recommendations.sort(Comparator
                .comparingInt((PurchaseRecommendationDto dto) -> statusOrder(dto.getRecommendationStatus()))
                .thenComparingInt(dto -> urgencyOrder(dto.getUrgency()))
                .thenComparing(PurchaseRecommendationDto::getItemName, Comparator.nullsLast(String::compareToIgnoreCase)));

        return recommendations;
    }

    private Map<Long, LeadTimeStats> buildLeadTimeStatsByItemId() {
        LocalDate since = LocalDate.now().minusDays(LEAD_TIME_LOOKBACK_DAYS);
        return purchaseOrderItemRepository.findRecentLeadTimeCandidates(since).stream()
                .map(this::toLeadTimeSample)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        LeadTimeSample::itemId,
                        Collectors.collectingAndThen(Collectors.toList(), LeadTimeStats::fromSamples)
                ));
    }

    private LeadTimeSample toLeadTimeSample(PurchaseOrderItem purchaseOrderItem) {
        LocalDate orderDate = purchaseOrderItem.getPurchaseOrder().getOrderDate();
        LocalDate expectedDate = purchaseOrderItem.getExpectedDate() != null
                ? purchaseOrderItem.getExpectedDate()
                : purchaseOrderItem.getPurchaseOrder().getExpectedDate();
        if (orderDate == null || expectedDate == null) {
            return null;
        }

        long daysBetween = ChronoUnit.DAYS.between(orderDate, expectedDate);
        if (daysBetween <= 0) {
            return null;
        }

        return new LeadTimeSample(purchaseOrderItem.getItem().getId(), (int) daysBetween);
    }

    private RecommendationState decideState(
            int currentStock,
            int availableStock,
            long outboundLast30Days,
            int outboundHistoryDays,
            LeadTimeStats leadTimeStats,
            int recommendedQty,
            double avgMonthlyUsage
    ) {
        if (currentStock < 0 || availableStock < 0 || looksAbnormal(recommendedQty, avgMonthlyUsage)) {
            return new RecommendationState(
                    STATUS_REVIEW,
                    "수동 검토 필요",
                    "재고 또는 계산 결과가 비정상 범위라 자동 추천을 확정하지 않았습니다."
            );
        }

        if (outboundLast30Days <= 0 || outboundHistoryDays < MIN_OUTBOUND_HISTORY_DAYS) {
            return new RecommendationState(
                    STATUS_DISABLED,
                    "최근 출고 이력이 부족합니다.",
                    "최근 30일 출고 근거가 부족해 추천 발주를 비활성화했습니다."
            );
        }

        if (leadTimeStats == null) {
            return new RecommendationState(
                    STATUS_DISABLED,
                    "리드타임 근거가 부족합니다.",
                    "발주 이력의 납기 근거가 없어 추천 발주를 비활성화했습니다."
            );
        }

        return new RecommendationState(
                STATUS_READY,
                "최근 출고와 발주 이력을 바탕으로 추천 가능합니다.",
                null
        );
    }

    private boolean looksAbnormal(int recommendedQty, double avgMonthlyUsage) {
        int abnormalThreshold = Math.max(200, (int) Math.ceil(avgMonthlyUsage * 3));
        return recommendedQty > abnormalThreshold;
    }

    private int calculateDaysUntilStockout(int currentStock, double avgDailyUsage) {
        if (avgDailyUsage <= 0) {
            return currentStock <= 0 ? 0 : 999;
        }
        return Math.max(0, (int) Math.floor(currentStock / avgDailyUsage));
    }

    private String resolveVendorName(Inventory inventory) {
        try {
            if (inventory.getItem().getDefaultVendor() != null) {
                return inventory.getItem().getDefaultVendor().getName();
            }
        } catch (Exception e) {
            log.debug("failed to resolve vendor for inventory {}", inventory.getId(), e);
        }
        return "-";
    }

    private String buildEvidenceSummary(int outboundHistoryDays, long outboundLast30Days, LeadTimeStats leadTimeStats) {
        String leadTimeText = leadTimeStats != null
                ? leadTimeStats.averageLeadTimeDays() + "일 리드타임"
                : "리드타임 근거 없음";
        return "최근 30일 출고 " + outboundLast30Days
                + " / 출고일 " + outboundHistoryDays + "일"
                + " / " + leadTimeText;
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private String calcUrgency(int currentStock, int safetyStock) {
        if (currentStock <= 0) {
            return "CRITICAL";
        }
        if (currentStock <= safetyStock / 2) {
            return "HIGH";
        }
        if (currentStock <= safetyStock) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private int urgencyOrder(String urgency) {
        return switch (urgency) {
            case "CRITICAL" -> 0;
            case "HIGH" -> 1;
            case "MEDIUM" -> 2;
            default -> 3;
        };
    }

    private int statusOrder(String recommendationStatus) {
        return switch (recommendationStatus) {
            case STATUS_READY -> 0;
            case STATUS_REVIEW -> 1;
            default -> 2;
        };
    }

    private record LeadTimeSample(Long itemId, int leadTimeDays) {
    }

    private record LeadTimeStats(int averageLeadTimeDays, int purchaseOrderCount) {
        private static LeadTimeStats fromSamples(List<LeadTimeSample> samples) {
            int averageLeadTime = (int) Math.max(1, Math.round((float) samples.stream()
                    .mapToInt(LeadTimeSample::leadTimeDays)
                    .average()
                    .orElse(DEFAULT_LEAD_TIME_DAYS)));
            return new LeadTimeStats(averageLeadTime, samples.size());
        }
    }

    private record RecommendationState(String status, String reason, String reviewMessage) {
    }
}
