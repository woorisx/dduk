package com.dduk.dto.inventory;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ValidationResultDto {
    private Long inventoryId;
    private Long warehouseId;
    private Long itemId;
    private int currentStock;
    private int calculatedStock;
    private boolean isMatch;
}
