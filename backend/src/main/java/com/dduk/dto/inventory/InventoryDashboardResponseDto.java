package com.dduk.dto.inventory;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryDashboardResponseDto {
    private Long totalQuantity;
    private BigDecimal totalValue;
    private Long lowStockCount;
    private Long outboundVolume30Days;
    private Long pendingTransferCount;
    private List<WarehouseDistributionDto> warehouseDistribution;
    private List<RecentMovementDto> recentMovements;
    private Map<String, Object> inventoryShortageRpa;
}
