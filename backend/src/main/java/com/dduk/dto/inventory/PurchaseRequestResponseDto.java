package com.dduk.dto.inventory;

import com.dduk.entity.inventory.PurchaseOrder;
import com.dduk.entity.inventory.PurchaseOrderItem;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class PurchaseRequestResponseDto {

    private Long purchaseOrderId;
    private String purchaseOrderNo;
    private String status;
    private Long itemId;
    private String itemName;
    private Long vendorId;
    private String vendorName;
    private Long requestedByMemberId;
    private String requestedByMemberName;
    private int quantity;
    private String unit;
    private BigDecimal unitPrice;
    private BigDecimal supplyAmount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private LocalDate expectedDate;
    private String note;

    public static PurchaseRequestResponseDto from(PurchaseOrder purchaseOrder, PurchaseOrderItem purchaseOrderItem) {
        return PurchaseRequestResponseDto.builder()
                .purchaseOrderId(purchaseOrder.getId())
                .purchaseOrderNo(purchaseOrder.getPurchaseOrderNo())
                .status(purchaseOrder.getStatus().name())
                .itemId(purchaseOrderItem.getItem().getId())
                .itemName(purchaseOrderItem.getItem().getName())
                .vendorId(purchaseOrder.getVendor().getId())
                .vendorName(purchaseOrder.getVendor().getName())
                .requestedByMemberId(purchaseOrder.getRequestedBy() != null ? purchaseOrder.getRequestedBy().getId() : null)
                .requestedByMemberName(purchaseOrder.getRequestedBy() != null ? purchaseOrder.getRequestedBy().getName() : null)
                .quantity(purchaseOrderItem.getQuantity())
                .unit(purchaseOrderItem.getUnit())
                .unitPrice(purchaseOrderItem.getUnitPrice())
                .supplyAmount(purchaseOrderItem.getSupplyAmount())
                .taxAmount(purchaseOrderItem.getTaxAmount())
                .totalAmount(purchaseOrder.getTotalAmount())
                .expectedDate(purchaseOrder.getExpectedDate())
                .note(purchaseOrder.getNote())
                .build();
    }
}
