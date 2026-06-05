package com.dduk.dto.inventory;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PurchaseRecommendationDto {
    private Long inventoryId;
    private Long itemId;
    private String itemCode;
    private String itemName;
    private String itemCategory;
    private String unit;
    private Long warehouseId;
    private String warehouseName;
    private int currentStock;
    private int safetyStock;
    private int allocatedStock;
    private int availableStock;
    private BigDecimal averageCost;
    private double avgMonthlyUsage;
    private int recommendedOrderQty;
    private String urgency;
    private int daysUntilStockout;
    private String defaultVendorName;
    private int leadTimeDays;
    private String recommendationStatus;
    private boolean orderable;
    private String statusReason;
    private String reviewMessage;
    private long outboundQuantityLast30Days;
    private int outboundHistoryDays;
    private int recentPurchaseOrderCount;
    private boolean leadTimeReliable;
    private String evidenceSummary;
}
