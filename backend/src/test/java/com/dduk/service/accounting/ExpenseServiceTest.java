package com.dduk.service.accounting;

import com.dduk.dto.accounting.ExpenseCreateRequest;
import com.dduk.dto.accounting.ExpenseStatusUpdateRequest;
import com.dduk.dto.accounting.ExpenseUpdateRequest;
import com.dduk.entity.accounting.Expense;
import com.dduk.repository.accounting.ExpenseRepository;
import com.dduk.repository.hr.EmployeeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExpenseService")
class ExpenseServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private ExpenseService expenseService;

    @Test
    @DisplayName("creates submitted expense by default")
    void createExpense_defaultStatus() {
        ExpenseCreateRequest request = createRequest();
        given(expenseRepository.save(org.mockito.ArgumentMatchers.any(Expense.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        expenseService.createExpense(request);

        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        then(expenseRepository).should().save(captor.capture());
        Expense saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("SUBMITTED");
        assertThat(saved.getCategory()).isEqualTo("소모품비");
    }

    @Test
    @DisplayName("rejects missing employee id")
    void createExpense_missingEmployee() {
        ExpenseCreateRequest request = createRequest();
        request.setEmployeeId(10L);
        given(employeeRepository.existsById(10L)).willReturn(false);

        assertThatThrownBy(() -> expenseService.createExpense(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Employee not found");
    }

    @Test
    @DisplayName("prevents editing approved expense")
    void updateExpense_approved() {
        Expense expense = expense("APPROVED");
        given(expenseRepository.findById(1L)).willReturn(Optional.of(expense));
        ExpenseUpdateRequest request = updateRequest();

        assertThatThrownBy(() -> expenseService.updateExpense(1L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Approved expenses cannot be edited");
    }

    @Test
    @DisplayName("prevents rejected to approved transition")
    void updateStatus_rejectedToApproved() {
        Expense expense = expense("REJECTED");
        given(expenseRepository.findById(1L)).willReturn(Optional.of(expense));
        ExpenseStatusUpdateRequest request = new ExpenseStatusUpdateRequest();
        request.setStatus("APPROVED");

        assertThatThrownBy(() -> expenseService.updateStatus(1L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Rejected expenses must be resubmitted");
    }

    private ExpenseCreateRequest createRequest() {
        ExpenseCreateRequest request = new ExpenseCreateRequest();
        request.setExpenseDate(LocalDate.of(2026, 6, 2));
        request.setCategory(" 소모품비 ");
        request.setAmount(new BigDecimal("12000"));
        request.setDescription("프린터 용지 구입");
        return request;
    }

    private ExpenseUpdateRequest updateRequest() {
        ExpenseUpdateRequest request = new ExpenseUpdateRequest();
        request.setExpenseDate(LocalDate.of(2026, 6, 2));
        request.setCategory("소모품비");
        request.setAmount(new BigDecimal("12000"));
        request.setDescription("프린터 용지 구입");
        return request;
    }

    private Expense expense(String status) {
        return Expense.builder()
                .employeeId(null)
                .expenseDate(LocalDate.of(2026, 6, 2))
                .category("소모품비")
                .amount(new BigDecimal("12000"))
                .description("프린터 용지 구입")
                .receiptFilePath(null)
                .status(status)
                .build();
    }
}
