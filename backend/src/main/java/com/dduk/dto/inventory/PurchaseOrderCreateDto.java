package com.dduk.dto.inventory;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
public class PurchaseOrderCreateDto {

    private Long vendorId;
    @JsonAlias("requested_by_member_id")
    private Long requestedByMemberId;
    @JsonAlias("approved_by_member_id")
    private Long approvedByMemberId;
    private LocalDate expectedDate;
    private String note;
    private List<PurchaseOrderItemCreateDto> items;
}
