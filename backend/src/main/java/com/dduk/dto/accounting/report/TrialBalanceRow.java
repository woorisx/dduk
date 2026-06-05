package com.dduk.dto.accounting.report;

import com.dduk.entity.accounting.AccountSide;
import com.dduk.entity.accounting.AccountType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class TrialBalanceRow {
    private Long accountId;
    private Long parentAccountId;
    private String accountCode;
    private String accountName;
    private AccountType accountType;
    private AccountSide normalBalance;
    private Integer level;
    private boolean leaf;
    private BigDecimal openingDebit;
    private BigDecimal openingCredit;
    private BigDecimal periodDebit;
    private BigDecimal periodCredit;
    private BigDecimal closingDebit;
    private BigDecimal closingCredit;
}
