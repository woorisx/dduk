package com.dduk.entity.accounting.payroll;

public enum PayrollTargetPeriodMode {
    BULK("일괄설정"),
    ITEM("항목별설정");

    private final String label;

    PayrollTargetPeriodMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
