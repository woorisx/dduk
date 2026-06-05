package com.dduk.entity.accounting.payroll;

public enum PayrollPayItemType {
    BASE_SALARY("기본급"),
    MEAL_ALLOWANCE("식대"),
    POSITION_ALLOWANCE("직책수당"),
    OVERTIME_ALLOWANCE("연장수당"),
    BONUS("상여금"),
    INCENTIVE("성과급");

    private final String label;

    PayrollPayItemType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
