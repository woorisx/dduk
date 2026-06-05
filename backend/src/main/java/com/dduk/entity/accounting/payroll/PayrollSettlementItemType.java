package com.dduk.entity.accounting.payroll;

public enum PayrollSettlementItemType {
    YEAR_END_TAX("연말정산"),
    HEALTH_INSURANCE_ADJUSTMENT("건강보험정산"),
    LONG_TERM_CARE_ADJUSTMENT("장기요양보험정산"),
    NATIONAL_PENSION_ADJUSTMENT("국민연금정산"),
    EMPLOYMENT_INSURANCE_ADJUSTMENT("고용보험정산");

    private final String label;

    PayrollSettlementItemType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
