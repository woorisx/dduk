package com.dduk.dto.inventory;

import com.dduk.entity.inventory.TransferStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseTransferResponseDto {
    private Long id;
    private String transferNo;
    private Long sourceWarehouseId;
    private String sourceWarehouseName;
    private Long targetWarehouseId;
    private String targetWarehouseName;
    private TransferStatus status;
    private String remarks;
    private String requestedByName;
    private String approvedByName;
    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;
    private LocalDateTime completedAt;
    private List<WarehouseTransferItemDto> items;
}
