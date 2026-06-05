package com.dduk.dto.accounting.payroll;

import com.dduk.entity.hr.Employee;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PayrollEmployeeSearchResponse {
    private Long id;
    private String employeeNo;
    private String name;
    private String department;
    private String position;
    private String employmentStatus;
    private String email;

    public static PayrollEmployeeSearchResponse from(Employee employee) {
        return PayrollEmployeeSearchResponse.builder()
                .id(employee.getId())
                .employeeNo(employee.getEmployeeNo())
                .name(employee.getName())
                .department(employee.getDepartment())
                .position(employee.getPosition())
                .employmentStatus(employee.getEmploymentStatus())
                .email(employee.getEmail())
                .build();
    }
}
