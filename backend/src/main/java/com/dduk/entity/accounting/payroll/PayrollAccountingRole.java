package com.dduk.entity.accounting.payroll;

import com.dduk.service.accounting.AccountingConstants;

public enum PayrollAccountingRole {
    PAYROLL_EXPENSE(AccountingConstants.PAYROLL_EXPENSE, "급여 계정"),
    BONUS_EXPENSE(AccountingConstants.PAYROLL_EXPENSE, "상여금 계정"),
    WELFARE_EXPENSE(AccountingConstants.WELFARE_EXPENSE, "복리후생비 계정"),
    WITHHOLDING_PAYABLE(AccountingConstants.WITHHOLDING_PAYABLE, "예수금 계정"),
    SALARY_PAYABLE(AccountingConstants.SALARY_PAYABLE, "미지급급여 계정"),
    BANK(AccountingConstants.BANK_ACCOUNT, "보통예금 계정");

    private final String defaultAccountCode;
    private final String label;

    PayrollAccountingRole(String defaultAccountCode, String label) {
        this.defaultAccountCode = defaultAccountCode;
        this.label = label;
    }

    public String getDefaultAccountCode() {
        return defaultAccountCode;
    }

    public String getLabel() {
        return label;
    }
}
