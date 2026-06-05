package com.dduk.dto.inventory;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseTransferItemDto {
    private Long itemId;
    private String itemCode;
    private String itemName;
    private String unit;
    private Integer quantity;
}
