package com.dduk.dto.inventory;

import com.dduk.entity.inventory.PurchaseOrderItem;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class PurchaseOrderItemResponseDto {

    private Long purchaseOrderItemId;
    private Long itemId;
    private String itemName;
    private int quantity;
    private String unit;
    private BigDecimal unitPrice;
    private BigDecimal supplyAmount;
    private BigDecimal taxAmount;
    private BigDecimal lineAmount;
    private LocalDate expectedDate;
    private String note;

    public static PurchaseOrderItemResponseDto from(PurchaseOrderItem purchaseOrderItem) {
        return PurchaseOrderItemResponseDto.builder()
                .purchaseOrderItemId(purchaseOrderItem.getId())
                .itemId(purchaseOrderItem.getItem().getId())
                .itemName(purchaseOrderItem.getItem().getName())
                .quantity(purchaseOrderItem.getQuantity())
                .unit(purchaseOrderItem.getUnit())
                .unitPrice(purchaseOrderItem.getUnitPrice())
                .supplyAmount(purchaseOrderItem.getSupplyAmount())
                .taxAmount(purchaseOrderItem.getTaxAmount())
                .lineAmount(purchaseOrderItem.getLineAmount())
                .expectedDate(purchaseOrderItem.getExpectedDate())
                .note(purchaseOrderItem.getNote())
                .build();
    }
}
