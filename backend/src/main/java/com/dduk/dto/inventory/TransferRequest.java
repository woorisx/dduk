package com.dduk.dto.inventory;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TransferRequest {
    private Long itemId;
    private Long fromWarehouseId;
    private Long toWarehouseId;
    private Integer quantity;
    private String referenceType;
    private String referenceId;
}
