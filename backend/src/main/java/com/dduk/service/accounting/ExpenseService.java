package com.dduk.service.accounting;

import com.dduk.dto.accounting.ExpenseCreateRequest;
import com.dduk.dto.accounting.ExpenseEmployeeResponse;
import com.dduk.dto.accounting.ExpensePageResponseDto;
import com.dduk.dto.accounting.ExpenseResponseDto;
import com.dduk.dto.accounting.ExpenseStatusUpdateRequest;
import com.dduk.dto.accounting.ExpenseSummaryResponse;
import com.dduk.dto.accounting.ExpenseUpdateRequest;
import com.dduk.entity.accounting.Expense;
import com.dduk.entity.admin.Member;
import com.dduk.entity.hr.Employee;
import com.dduk.repository.accounting.ExpenseRepository;
import com.dduk.repository.admin.MemberRepository;
import com.dduk.repository.hr.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseService {

    private static final String DEFAULT_STATUS = "SUBMITTED";
    private static final Set<String> ALLOWED_STATUSES = Set.of("PENDING", "SUBMITTED", "APPROVED", "REJECTED");

    private final ExpenseRepository expenseRepository;
    private final EmployeeRepository employeeRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public ExpenseEmployeeResponse getCurrentEmployee(Long memberId) {
        return findOrCreateEmployee(memberId)
                .map(ExpenseEmployeeResponse::from)
                .orElse(null);
    }

    @Transactional
    public Long getCurrentEmployeeIdOrNull(Long memberId) {
        return findOrCreateEmployee(memberId)
                .map(Employee::getId)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponseDto> getExpenses(
            String status,
            Long employeeId,
            LocalDate startDate,
            LocalDate endDate,
            String keyword
    ) {
        String normalizedStatus = normalizeOptionalStatus(status);
        validateEmployee(employeeId);
        validateDateRange(startDate, endDate);
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;

        return expenseRepository.findWithFilters(normalizedStatus, employeeId, startDate, endDate, normalizedKeyword)
                .stream()
                .map(ExpenseResponseDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExpensePageResponseDto getExpensesPaged(
            String status,
            Long employeeId,
            LocalDate startDate,
            LocalDate endDate,
            String keyword,
            int page,
            int size
    ) {
        String normalizedStatus = normalizeOptionalStatus(status);
        validateEmployee(employeeId);
        validateDateRange(startDate, endDate);
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "expenseDate", "id"));
        Page<ExpenseResponseDto> result = expenseRepository
                .findWithFiltersPage(normalizedStatus, employeeId, startDate, endDate, normalizedKeyword, pageRequest)
                .map(ExpenseResponseDto::fromEntity);
        return ExpensePageResponseDto.from(result);
    }

    @Transactional(readOnly = true)
    public ExpenseSummaryResponse getSummary(
            String status,
            Long employeeId,
            LocalDate startDate,
            LocalDate endDate,
            String keyword
    ) {
        List<ExpenseResponseDto> expenses = getExpenses(status, employeeId, startDate, endDate, keyword);
        BigDecimal totalAmount = expenses.stream()
                .map(ExpenseResponseDto::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ExpenseSummaryResponse.builder()
                .totalCount(expenses.size())
                .totalAmount(totalAmount)
                .pendingCount(countByStatus(expenses, "PENDING"))
                .submittedCount(countByStatus(expenses, "SUBMITTED"))
                .approvedCount(countByStatus(expenses, "APPROVED"))
                .rejectedCount(countByStatus(expenses, "REJECTED"))
                .build();
    }

    @Transactional(readOnly = true)
    public ExpenseResponseDto getExpense(Long id) {
        return ExpenseResponseDto.fromEntity(findById(id));
    }

    @Transactional
    public ExpenseResponseDto createExpense(ExpenseCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Expense request is required.");
        }
        validateExpenseRequest(request.getEmployeeId(), request.getExpenseDate(), request.getCategory(), request.getAmount(), request.getDescription());
        return createExpense(
                request.getEmployeeId(),
                request.getExpenseDate(),
                request.getCategory(),
                request.getAmount(),
                request.getDescription(),
                null,
                normalizeStatusOrDefault(request.getStatus())
        );
    }

    @Transactional
    public ExpenseResponseDto createExpense(
            Long employeeId,
            LocalDate expenseDate,
            String category,
            BigDecimal amount,
            String description,
            String receiptFilePath,
            String status
    ) {
        validateExpenseRequest(employeeId, expenseDate, category, amount, description);
        Expense expense = Expense.builder()
                .employeeId(employeeId)
                .expenseDate(expenseDate)
                .category(category.trim())
                .amount(amount)
                .description(description.trim())
                .receiptFilePath(receiptFilePath)
                .status(normalizeStatusOrDefault(status))
                .build();

        return ExpenseResponseDto.fromEntity(expenseRepository.save(expense));
    }

    @Transactional
    public ExpenseResponseDto updateExpense(Long id, ExpenseUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Expense request is required.");
        }
        validateExpenseRequest(request.getEmployeeId(), request.getExpenseDate(), request.getCategory(), request.getAmount(), request.getDescription());
        Expense expense = findById(id);
        if ("APPROVED".equals(expense.getStatus())) {
            throw new IllegalStateException("Approved expenses cannot be edited.");
        }
        expense.updateDetails(
                request.getEmployeeId(),
                request.getExpenseDate(),
                request.getCategory().trim(),
                request.getAmount(),
                request.getDescription().trim()
        );
        return ExpenseResponseDto.fromEntity(expense);
    }

    @Transactional
    public ExpenseResponseDto updateStatus(Long id, ExpenseStatusUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Expense status request is required.");
        }
        Expense expense = findById(id);
        String nextStatus = normalizeRequiredStatus(request.getStatus());
        validateStatusTransition(expense.getStatus(), nextStatus);
        expense.updateStatus(nextStatus);
        return ExpenseResponseDto.fromEntity(expense);
    }

    @Transactional
    public void deleteExpense(Long id) {
        Expense expense = findById(id);
        if ("APPROVED".equals(expense.getStatus())) {
            throw new IllegalStateException("Approved expenses cannot be deleted.");
        }
        expenseRepository.delete(expense);
    }

    private Expense findById(Long id) {
        return expenseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Expense not found: " + id));
    }

    private void validateExpenseRequest(Long employeeId, LocalDate expenseDate, String category, BigDecimal amount, String description) {
        validateEmployee(employeeId);
        if (expenseDate == null) {
            throw new IllegalArgumentException("expenseDate is required.");
        }
        if (!StringUtils.hasText(category)) {
            throw new IllegalArgumentException("category is required.");
        }
        if (category.trim().length() > 50) {
            throw new IllegalArgumentException("category must be 50 characters or fewer.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero.");
        }
        if (!StringUtils.hasText(description)) {
            throw new IllegalArgumentException("description is required.");
        }
        if (description.trim().length() > 255) {
            throw new IllegalArgumentException("description must be 255 characters or fewer.");
        }
    }

    private void validateEmployee(Long employeeId) {
        if (employeeId != null && !employeeRepository.existsById(employeeId)) {
            throw new IllegalArgumentException("Employee not found: " + employeeId);
        }
    }

    private Optional<Employee> findOrCreateEmployee(Long memberId) {
        if (memberId == null) {
            return Optional.empty();
        }
        Optional<Employee> existing = employeeRepository.findByMemberId(memberId);
        if (existing.isPresent()) {
            return existing;
        }
        return memberRepository.findById(memberId)
                .map(this::createEmployeeFromMember);
    }

    private Employee createEmployeeFromMember(Member member) {
        String employeeNo = "EMP-" + String.format("%05d", member.getId());
        String name = member.getName();
        String email = member.getLoginId() + "@dduk.local";

        // 사번이 이미 존재하면 기존 직원을 member_id로 연결
        Optional<Employee> byNo = employeeRepository.findByEmployeeNo(employeeNo);
        if (byNo.isPresent()) {
            Employee emp = byNo.get();
            if (emp.getMemberId() == null) {
                emp.setMemberId(member.getId());
            }
            return emp;
        }

        // 이메일이 이미 존재하면 기존 직원을 member_id로 연결
        Optional<Employee> byEmail = employeeRepository.findByEmail(email);
        if (byEmail.isPresent()) {
            Employee emp = byEmail.get();
            if (emp.getMemberId() == null) {
                emp.setMemberId(member.getId());
            }
            return emp;
        }

        Employee employee = Employee.builder()
                .memberId(member.getId())
                .employeeNo(employeeNo)
                .name(name)
                .department("미배정")
                .position("사원")
                .employmentStatus("ACTIVE")
                .hireDate(LocalDate.now())
                .email(email)
                .build();

        Employee saved = employeeRepository.save(employee);
        log.info("Member(id={})에 대한 Employee(id={}, no={})를 자동 생성했습니다.",
                member.getId(), saved.getId(), employeeNo);
        return saved;
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate must be on or before endDate.");
        }
    }

    private String normalizeOptionalStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        return normalizeRequiredStatus(status);
    }

    private String normalizeStatusOrDefault(String status) {
        return StringUtils.hasText(status) ? normalizeRequiredStatus(status) : DEFAULT_STATUS;
    }

    private String normalizeRequiredStatus(String status) {
        if (!StringUtils.hasText(status)) {
            throw new IllegalArgumentException("status is required.");
        }
        String normalized = status.trim().toUpperCase();
        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported expense status: " + status);
        }
        return normalized;
    }

    private void validateStatusTransition(String currentStatus, String nextStatus) {
        if ("APPROVED".equals(currentStatus) && "PENDING".equals(nextStatus)) {
            throw new IllegalStateException("Approved expenses cannot return to PENDING.");
        }
        if ("REJECTED".equals(currentStatus) && "APPROVED".equals(nextStatus)) {
            throw new IllegalStateException("Rejected expenses must be resubmitted before approval.");
        }
    }

    private long countByStatus(List<ExpenseResponseDto> expenses, String status) {
        return expenses.stream()
                .filter(expense -> status.equals(expense.getStatus()))
                .count();
    }
}
