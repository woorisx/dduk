package com.dduk.entity.accounting.payroll;

public enum PayrollType {
    SALARY("급여"),
    BONUS("상여"),
    INCENTIVE("성과급"),
    SEVERANCE("퇴직금"),
    OTHER_ALLOWANCE("기타수당");

    private final String label;

    PayrollType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
