package com.dduk.dto.accounting.voucher;

import com.dduk.entity.accounting.voucher.enums.VatType;
import com.dduk.entity.accounting.voucher.enums.VoucherStatus;
import com.dduk.entity.accounting.voucher.enums.VoucherType;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class VoucherResponse {
    private Long id;
    private String voucherNo;
    private LocalDate voucherDate;
    private VoucherType voucherType;
    private VatType vatType;
    private Long vendorId;
    private String vendorNameSnapshot;
    private VoucherStatus status;
    private String description;
    private Long journalEntryId;
    private String createdBy;
    private LocalDateTime createdAt;
    private List<VoucherLineResponse> lines;
}
