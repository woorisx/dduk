package com.dduk.entity.accounting.payroll;

public enum PayrollDeductionType {
    NATIONAL_PENSION("국민연금"),
    HEALTH_INSURANCE("건강보험"),
    LONG_TERM_CARE("장기요양보험"),
    EMPLOYMENT_INSURANCE("고용보험"),
    INCOME_TAX("소득세"),
    LOCAL_INCOME_TAX("지방소득세");

    private final String label;

    PayrollDeductionType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
