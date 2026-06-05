package com.dduk.entity.accounting.payroll;

public enum PayrollTaxType {
    TAXABLE("과세"),
    NON_TAXABLE("비과세"),
    MIXED("혼합");

    private final String label;

    PayrollTaxType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
