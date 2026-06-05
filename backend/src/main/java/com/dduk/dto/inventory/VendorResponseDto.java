package com.dduk.dto.inventory;

import com.dduk.entity.inventory.Vendor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class VendorResponseDto {

    private Long id;
    private String vendorCode;
    private String businessRegistrationNo;
    private String name;
    private String representativeName;
    private String businessType;
    private String businessItem;
    private String contactName;
    private String contactPhone;
    private String email;
    private String address;
    private String bankName;
    private String bankAccountNo;
    private String bankAccountHolder;
    private String bankbookCopyFilePath;
    private String status;
    private String memo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static VendorResponseDto from(Vendor vendor) {
        return VendorResponseDto.builder()
                .id(vendor.getId())
                .vendorCode(vendor.getVendorCode())
                .businessRegistrationNo(vendor.getBusinessRegistrationNo())
                .name(vendor.getName())
                .representativeName(vendor.getRepresentativeName())
                .businessType(vendor.getBusinessType())
                .businessItem(vendor.getBusinessItem())
                .contactName(vendor.getContactName())
                .contactPhone(vendor.getContactPhone())
                .email(vendor.getEmail())
                .address(vendor.getAddress())
                .bankName(vendor.getBankName())
                .bankAccountNo(vendor.getBankAccountNo())
                .bankAccountHolder(vendor.getBankAccountHolder())
                .bankbookCopyFilePath(vendor.getBankbookCopyFilePath())
                .status(vendor.getStatus())
                .memo(vendor.getMemo())
                .createdAt(vendor.getCreatedAt())
                .updatedAt(vendor.getUpdatedAt())
                .build();
    }
}
