package com.dduk.dto.inventory;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseTransferRequestDto {
    private Long sourceWarehouseId;
    private Long targetWarehouseId;
    private String remarks;
    private List<WarehouseTransferItemDto> items;
}
