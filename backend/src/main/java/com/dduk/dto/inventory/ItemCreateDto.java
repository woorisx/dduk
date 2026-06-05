package com.dduk.dto.inventory;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class ItemCreateDto {

    private String name;
    private String category;
    private String spec;
    private String unit;
    private BigDecimal unitPrice;
    private Long vendorId;
    private Long registeredById;
}
