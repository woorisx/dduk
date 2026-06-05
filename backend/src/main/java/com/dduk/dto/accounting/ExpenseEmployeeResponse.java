package com.dduk.dto.accounting;

import com.dduk.entity.hr.Employee;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExpenseEmployeeResponse {

    private Long id;
    private String employeeNo;
    private String name;
    private String department;
    private String position;

    public static ExpenseEmployeeResponse from(Employee employee) {
        return ExpenseEmployeeResponse.builder()
                .id(employee.getId())
                .employeeNo(employee.getEmployeeNo())
                .name(employee.getName())
                .department(employee.getDepartment())
                .position(employee.getPosition())
                .build();
    }
}
