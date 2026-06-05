package com.dduk.dto.accounting;

import com.dduk.entity.accounting.Expense;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class ExpenseResponseDto {

    private Long id;
    private Long employeeId;
    private LocalDate expenseDate;
    private String category;
    private BigDecimal amount;
    private String description;
    private String receiptFilePath;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ExpenseResponseDto fromEntity(Expense expense) {
        return ExpenseResponseDto.builder()
                .id(expense.getId())
                .employeeId(expense.getEmployeeId())
                .expenseDate(expense.getExpenseDate())
                .category(expense.getCategory())
                .amount(expense.getAmount())
                .description(expense.getDescription())
                .receiptFilePath(null)
                .status(expense.getStatus())
                .createdAt(expense.getCreatedAt())
                .updatedAt(expense.getUpdatedAt())
                .build();
    }
}
