package com.dduk.dto.accounting.dashboard;

import com.dduk.entity.accounting.AccountType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class AccountCompositionResponse {
    private AccountType accountType;
    private String label;
    private BigDecimal amount;
    private BigDecimal ratio;
}
