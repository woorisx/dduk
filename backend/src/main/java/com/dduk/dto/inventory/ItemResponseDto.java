package com.dduk.dto.inventory;

import com.dduk.entity.inventory.Item;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class ItemResponseDto {

    private Long id;
    private String itemCode;
    private String barcode;
    private String name;
    private String category;
    private String spec;
    private String unit;
    private BigDecimal unitPrice;
    private Long defaultVendorId;
    private String defaultVendorName;
    private Long registeredById;
    private String registeredByName;

    public static ItemResponseDto from(Item item) {
        return ItemResponseDto.builder()
                .id(item.getId())
                .itemCode(item.getItemCode())
                .barcode(item.getBarcode())
                .name(item.getName())
                .category(item.getCategory())
                .spec(item.getSpec())
                .unit(item.getUnit())
                .unitPrice(item.getUnitPrice())
                .defaultVendorId(item.getDefaultVendor() == null ? null : item.getDefaultVendor().getId())
                .defaultVendorName(item.getDefaultVendor() == null ? null : item.getDefaultVendor().getName())
                .registeredById(item.getRegisteredBy() == null ? null : item.getRegisteredBy().getId())
                .registeredByName(item.getRegisteredBy() == null ? null : item.getRegisteredBy().getName())
                .build();
    }
}
