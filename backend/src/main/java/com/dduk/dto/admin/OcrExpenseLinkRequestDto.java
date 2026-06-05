package com.dduk.dto.admin;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class OcrExpenseLinkRequestDto {

    private Long employeeId;
    private LocalDate expenseDate;
    private String category;
    private BigDecimal amount;
    private String description;
    private String status;
}
