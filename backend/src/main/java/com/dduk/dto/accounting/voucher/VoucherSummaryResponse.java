package com.dduk.dto.accounting.voucher;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoucherSummaryResponse {
    private long todayCount;
    private long draftCount;
    private long requestedCount;
    private long approvedCount;
    private long postedCount;
    private long totalVoucherCount;
    private long totalVouchers;
    private BigDecimal totalSupplyAmount;
    private BigDecimal totalVatAmount;
    private BigDecimal totalAmount;
}
