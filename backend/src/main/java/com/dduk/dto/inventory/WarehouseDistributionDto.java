package com.dduk.dto.inventory;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseDistributionDto {
    private String warehouseName;
    private Long totalStock;
    private BigDecimal totalValue;
}
