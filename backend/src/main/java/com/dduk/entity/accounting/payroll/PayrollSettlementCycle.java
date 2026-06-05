package com.dduk.entity.accounting.payroll;

public enum PayrollSettlementCycle {
    MONTHLY("월정산"),
    QUARTERLY("분기정산"),
    HALF_YEARLY("반기정산"),
    YEARLY("연정산");

    private final String label;

    PayrollSettlementCycle(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
