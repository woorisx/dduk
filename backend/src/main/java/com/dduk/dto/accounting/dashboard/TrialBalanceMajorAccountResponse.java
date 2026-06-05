package com.dduk.dto.accounting.dashboard;

import com.dduk.entity.accounting.AccountType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class TrialBalanceMajorAccountResponse {
    private String accountCode;
    private String accountName;
    private AccountType accountType;
    private BigDecimal closingBalance;
}
