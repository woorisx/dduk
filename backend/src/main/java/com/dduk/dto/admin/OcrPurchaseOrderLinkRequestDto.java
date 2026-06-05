package com.dduk.dto.admin;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@NoArgsConstructor
public class OcrPurchaseOrderLinkRequestDto {

    private Long vendorId;
    private Long approvedByMemberId;
    private LocalDate expectedDate;
    private String note;
    private List<Item> items;

    @Getter
    @NoArgsConstructor
    public static class Item {
        private Long itemId;
        private String itemName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private LocalDate expectedDate;
        private String note;
    }
}
