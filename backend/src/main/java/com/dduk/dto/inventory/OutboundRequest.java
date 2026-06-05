package com.dduk.dto.inventory;

import com.dduk.entity.inventory.MovementReason;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OutboundRequest {
    private Long itemId;
    private Long warehouseId;
    private int quantity;
    private MovementReason reason;
    private String referenceType;
    private String referenceId;
}
