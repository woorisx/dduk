package com.dduk.dto.inventory;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
public class PurchaseRequestCreateDto {

    private Long itemId;
    private Long vendorId;
    @JsonAlias("requested_by_member_id")
    private Long requestedByMemberId;
    private Integer quantity;
    private BigDecimal unitPrice;
    private LocalDate expectedDate;
    private String note;
}
