package com.dduk.dto.inventory;

import com.dduk.entity.inventory.MovementType;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecentMovementDto {
    private Long id;
    private LocalDateTime createdAt;
    private String referenceNo;
    private MovementType movementType;
    private String itemName;
    private Integer quantity;
    private String warehouseName;
}
