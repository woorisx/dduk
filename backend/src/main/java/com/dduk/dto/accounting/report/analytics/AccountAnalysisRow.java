package com.dduk.dto.accounting.report.analytics;

import com.dduk.entity.accounting.AccountSide;
import com.dduk.entity.accounting.AccountType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class AccountAnalysisRow {
    private Long accountId;
    private Long parentAccountId;
    private String accountCode;
    private String accountName;
    private AccountType accountType;
    private AccountSide normalBalance;
    private Integer level;
    private boolean leaf;
    private BigDecimal openingBalance;
    private BigDecimal periodDebit;
    private BigDecimal periodCredit;
    private BigDecimal periodChange;
    private BigDecimal closingBalance;
}
