package com.dduk.dto.accounting;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class ExpenseCreateRequest {

    private Long employeeId;
    private LocalDate expenseDate;
    private String category;
    private BigDecimal amount;
    private String description;
    private String status;
}
