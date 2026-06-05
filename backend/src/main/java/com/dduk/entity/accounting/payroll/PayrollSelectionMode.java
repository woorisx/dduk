package com.dduk.entity.accounting.payroll;

public enum PayrollSelectionMode {
    ALL("전체"),
    SELECTED("선택");

    private final String label;

    PayrollSelectionMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
