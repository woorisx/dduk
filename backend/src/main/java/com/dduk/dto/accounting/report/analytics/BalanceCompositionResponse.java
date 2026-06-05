package com.dduk.dto.accounting.report.analytics;

import com.dduk.entity.accounting.AccountType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class BalanceCompositionResponse {
    private AccountType accountType;
    private String label;
    private BigDecimal amount;
    private BigDecimal ratio;
}
