package com.dduk.dto.inventory;

import com.dduk.entity.inventory.PurchaseOrder;
import com.dduk.entity.inventory.PurchaseOrderItem;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class PurchaseOrderResponseDto {

    private Long purchaseOrderId;
    private String purchaseOrderNo;
    private String status;
    private Long vendorId;
    private String vendorName;
    private Long requestedByMemberId;
    private String requestedByMemberName;
    private Long approvedByMemberId;
    private String approvedByMemberName;
    private LocalDate orderDate;
    private LocalDate expectedDate;
    private LocalDateTime createdAt;
    private BigDecimal totalAmount;
    private String note;
    private List<PurchaseOrderItemResponseDto> items;

    public static PurchaseOrderResponseDto from(PurchaseOrder purchaseOrder, List<PurchaseOrderItem> purchaseOrderItems) {
        return PurchaseOrderResponseDto.builder()
                .purchaseOrderId(purchaseOrder.getId())
                .purchaseOrderNo(purchaseOrder.getPurchaseOrderNo())
                .status(purchaseOrder.getStatus().name())
                .vendorId(purchaseOrder.getVendor().getId())
                .vendorName(purchaseOrder.getVendor().getName())
                .requestedByMemberId(purchaseOrder.getRequestedBy() != null ? purchaseOrder.getRequestedBy().getId() : null)
                .requestedByMemberName(purchaseOrder.getRequestedBy() != null ? purchaseOrder.getRequestedBy().getName() : null)
                .approvedByMemberId(purchaseOrder.getApprovedBy() == null ? null : purchaseOrder.getApprovedBy().getId())
                .approvedByMemberName(purchaseOrder.getApprovedBy() == null ? null : purchaseOrder.getApprovedBy().getName())
                .orderDate(purchaseOrder.getOrderDate())
                .expectedDate(purchaseOrder.getExpectedDate())
                .createdAt(purchaseOrder.getCreatedAt())
                .totalAmount(purchaseOrder.getTotalAmount())
                .note(purchaseOrder.getNote())
                .items(purchaseOrderItems.stream()
                        .map(PurchaseOrderItemResponseDto::from)
                        .toList())
                .build();
    }
}
