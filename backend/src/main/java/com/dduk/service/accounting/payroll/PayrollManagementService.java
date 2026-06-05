package com.dduk.service.accounting.payroll;

import com.dduk.dto.accounting.payroll.*;
import com.dduk.entity.accounting.payroll.*;
import com.dduk.repository.accounting.payroll.PayrollLedgerRepository;
import com.dduk.service.accounting.period.MonthlyClosingService;
import com.dduk.entity.accounting.Account;
import com.dduk.entity.accounting.JournalEntry;
import com.dduk.entity.accounting.JournalLine;
import com.dduk.entity.hr.Employee;
import com.dduk.entity.hr.PayrollContract;
import com.dduk.repository.accounting.AccountRepository;
import com.dduk.repository.accounting.JournalEntryRepository;
import com.dduk.repository.hr.EmployeeRepository;
import com.dduk.repository.hr.PayrollContractRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PayrollManagementService {

    private static final String SOURCE_TYPE = "PAYROLL_LEDGER";
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final PayrollLedgerRepository payrollLedgerRepository;
    private final EmployeeRepository employeeRepository;
    private final PayrollContractRepository payrollContractRepository;
    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final MonthlyClosingService monthlyClosingService;

    @Transactional
    public PayrollLedgerResponse createLedger(PayrollLedgerCreateRequest request, boolean calculateNow) {
        validateCreateRequest(request);
        monthlyClosingService.assertPeriodMutable(request.getPaymentDate());

        PayrollLedger ledger = PayrollLedger.builder()
                .attributionYearMonth(request.getAttributionYearMonth())
                .payrollType(request.getPayrollType())
                .taxType(request.getTaxType())
                .settlementCycle(request.getSettlementCycle())
                .targetPeriodMode(request.getTargetPeriodMode())
                .paymentDate(request.getPaymentDate())
                .paymentYearMonth(request.getPaymentYearMonth())
                .ledgerName(request.getLedgerName())
                .settlementItemSelectionMode(request.getSettlementItemSelectionMode())
                .employeeSelectionMode(request.getEmployeeSelectionMode())
                .bonusRateOrAmount(request.getBonusRateOrAmount())
                .status(PayrollStatus.READY)
                .createdBy(StringUtils.hasText(request.getCreatedBy()) ? request.getCreatedBy() : "system")
                .build();

        if (request.getSettlementItemSelectionMode() == PayrollSelectionMode.ALL) {
            for (PayrollSettlementItemType type : PayrollSettlementItemType.values()) {
                ledger.addSettlementItem(settlementItem(type));
            }
        } else {
            request.getSettlementItems().forEach(type -> ledger.addSettlementItem(settlementItem(type)));
        }
        if (request.getEmployeeSelectionMode() == PayrollSelectionMode.SELECTED) {
            employeeRepository.findAllById(request.getEmployeeIds()).forEach(employee -> ledger.addEmployee(targetEmployeeRow(employee)));
        }

        PayrollLedger saved = payrollLedgerRepository.save(ledger);
        if (calculateNow) {
            saved = calculateLedgerEntity(saved.getId());
        }
        return toResponse(saved, true);
    }

    @Transactional
    public PayrollLedgerResponse calculateLedger(Long ledgerId) {
        return toResponse(calculateLedgerEntity(ledgerId), true);
    }

    @Transactional
    public PayrollLedgerResponse confirmLedger(Long ledgerId) {
        PayrollLedger ledger = findLedger(ledgerId);
        monthlyClosingService.assertPeriodMutable(ledger.getPaymentDate());
        if (ledger.getStatus() != PayrollStatus.CALCULATED) {
            throw new IllegalStateException("Only CALCULATED payroll ledgers can be confirmed.");
        }
        ledger.setStatus(PayrollStatus.CONFIRMED);
        if (ledger.getJournalEntry() != null) {
            ledger.getJournalEntry().post();
            journalEntryRepository.save(ledger.getJournalEntry());
        }
        return toResponse(ledger, true);
    }

    @Transactional(readOnly = true)
    public List<PayrollLedgerResponse> getLedgers() {
        return payrollLedgerRepository.findTop100ByOrderByPaymentDateDescIdDesc()
                .stream()
                .map(ledger -> toResponse(ledger, false))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PayrollLedgerResponse getLedger(Long ledgerId) {
        return toResponse(findLedger(ledgerId), true);
    }

    @Transactional(readOnly = true)
    public List<PayrollPayslipResponse> getPayslips(Long ledgerId) {
        PayrollLedger ledger = findLedger(ledgerId);
        return ledger.getEmployees().stream()
                .map(employee -> PayrollPayslipResponse.builder()
                        .ledgerId(ledger.getId())
                        .ledgerName(ledger.getLedgerName())
                        .attributionYearMonth(ledger.getAttributionYearMonth())
                        .paymentDate(ledger.getPaymentDate())
                        .employeeNo(employee.getEmployeeNoSnapshot())
                        .employeeName(employee.getEmployeeNameSnapshot())
                        .department(employee.getDepartmentSnapshot())
                        .position(employee.getPositionSnapshot())
                        .grossAmount(employee.getGrossAmount())
                        .deductionAmount(employee.getDeductionAmount())
                        .netAmount(employee.getNetAmount())
                        .payItems(mapPayItems(employee))
                        .deductionItems(mapDeductionItems(employee))
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PayrollEmployeeSearchResponse> searchEmployees(String keyword) {
        String normalized = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return employeeRepository.searchForPayroll(normalized)
                .stream()
                .map(PayrollEmployeeSearchResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PayrollSummaryResponse getSummary() {
        List<PayrollStatus> payableStatuses = List.of(PayrollStatus.CALCULATED, PayrollStatus.CONFIRMED, PayrollStatus.POSTED);
        return new PayrollSummaryResponse(
                payrollLedgerRepository.countByStatus(PayrollStatus.READY)
                        + payrollLedgerRepository.countByStatus(PayrollStatus.CALCULATED),
                payrollLedgerRepository.countByStatus(PayrollStatus.CALCULATED),
                payrollLedgerRepository.sumNetAmountByStatusIn(payableStatuses),
                payrollLedgerRepository.sumDeductionAmountByStatusIn(payableStatuses),
                payrollLedgerRepository.sumNetAmountByStatusIn(payableStatuses),
                payrollLedgerRepository.countByStatus(PayrollStatus.READY)
        );
    }

    private PayrollLedger calculateLedgerEntity(Long ledgerId) {
        PayrollLedger ledger = findLedger(ledgerId);
        monthlyClosingService.assertPeriodMutable(ledger.getPaymentDate());
        if (ledger.getStatus() == PayrollStatus.CONFIRMED || ledger.getStatus() == PayrollStatus.POSTED || ledger.getStatus() == PayrollStatus.CLOSED) {
            throw new IllegalStateException("Confirmed or closed payroll ledgers cannot be recalculated.");
        }

        List<Employee> employees = resolveTargetEmployees(ledger);
        if (employees.isEmpty()) {
            throw new IllegalArgumentException("At least one payroll target employee is required.");
        }

        Map<Long, PayrollContract> contracts = payrollContractRepository.findByEmployeeIdIn(
                        employees.stream().map(Employee::getId).collect(Collectors.toList()))
                .stream()
                .collect(Collectors.toMap(contract -> contract.getEmployee().getId(), Function.identity()));

        ledger.clearCalculatedEmployees();
        for (Employee employee : employees) {
            PayrollContract contract = contracts.get(employee.getId());
            if (contract == null) {
                throw new IllegalStateException("Payroll contract is missing for employee: " + employee.getEmployeeNo());
            }
            ledger.addEmployee(calculateEmployee(ledger, employee, contract));
        }

        ledger.applyTotals();
        ledger.setPreEmployeeChecked(true);
        ledger.setPreInsuranceCalculated(true);
        ledger.setPreSettlementValidated(true);
        ledger.setPreAccountValidated(true);
        ledger.setStatus(PayrollStatus.CALCULATED);
        ledger.setJournalEntry(createOrReplaceJournalEntry(ledger));
        return ledger;
    }

    private PayrollLedgerEmployee calculateEmployee(PayrollLedger ledger, Employee employee, PayrollContract contract) {
        BigDecimal baseSalary = nonNegative(contract.getBaseSalary(), "baseSalary");
        BigDecimal mealAllowance = ledger.getTaxType() == PayrollTaxType.NON_TAXABLE ? new BigDecimal("200000") : new BigDecimal("100000");
        BigDecimal positionAllowance = baseSalary.multiply(new BigDecimal("0.05")).setScale(0, RoundingMode.DOWN);
        BigDecimal overtimeAllowance = ZERO;
        BigDecimal bonus = calculateBonus(ledger, baseSalary);

        PayrollLedgerEmployee row = PayrollLedgerEmployee.builder()
                .employee(employee)
                .employeeNoSnapshot(employee.getEmployeeNo())
                .employeeNameSnapshot(employee.getName())
                .departmentSnapshot(employee.getDepartment())
                .positionSnapshot(employee.getPosition())
                .bankAccountSnapshot("")
                .paymentStatus("READY")
                .build();

        row.addPayItem(payItem(PayrollPayItemType.BASE_SALARY, baseSalary, true, 1));
        row.addPayItem(payItem(PayrollPayItemType.MEAL_ALLOWANCE, mealAllowance, ledger.getTaxType() != PayrollTaxType.NON_TAXABLE, 2));
        row.addPayItem(payItem(PayrollPayItemType.POSITION_ALLOWANCE, positionAllowance, true, 3));
        if (overtimeAllowance.compareTo(ZERO) > 0) {
            row.addPayItem(payItem(PayrollPayItemType.OVERTIME_ALLOWANCE, overtimeAllowance, true, 4));
        }
        if (bonus.compareTo(ZERO) > 0) {
            row.addPayItem(payItem(ledger.getPayrollType() == PayrollType.INCENTIVE ? PayrollPayItemType.INCENTIVE : PayrollPayItemType.BONUS, bonus, true, 5));
        }

        BigDecimal gross = row.getPayItems().stream().map(PayrollItem::getAmount).reduce(ZERO, BigDecimal::add);
        BigDecimal taxableGross = row.getPayItems().stream()
                .filter(PayrollItem::getTaxable)
                .map(PayrollItem::getAmount)
                .reduce(ZERO, BigDecimal::add);

        row.addDeductionItem(deductionItem(PayrollDeductionType.NATIONAL_PENSION, taxableGross.multiply(new BigDecimal("0.045")), 1));
        row.addDeductionItem(deductionItem(PayrollDeductionType.HEALTH_INSURANCE, taxableGross.multiply(new BigDecimal("0.03545")), 2));
        BigDecimal health = row.getDeductionItems().stream()
                .filter(item -> item.getDeductionType() == PayrollDeductionType.HEALTH_INSURANCE)
                .findFirst()
                .map(PayrollDeductionItem::getAmount)
                .orElse(ZERO);
        row.addDeductionItem(deductionItem(PayrollDeductionType.LONG_TERM_CARE, health.multiply(new BigDecimal("0.1295")), 3));
        row.addDeductionItem(deductionItem(PayrollDeductionType.EMPLOYMENT_INSURANCE, taxableGross.multiply(new BigDecimal("0.009")), 4));
        BigDecimal incomeTax = calculateIncomeTax(taxableGross);
        row.addDeductionItem(deductionItem(PayrollDeductionType.INCOME_TAX, incomeTax, 5));
        row.addDeductionItem(deductionItem(PayrollDeductionType.LOCAL_INCOME_TAX, incomeTax.multiply(new BigDecimal("0.1")), 6));

        row.applyTotals();
        if (row.getNetAmount().compareTo(ZERO) < 0 || gross.compareTo(row.getDeductionAmount()) < 0) {
            throw new IllegalStateException("Net payroll amount cannot be negative: " + employee.getEmployeeNo());
        }
        return row;
    }

    private JournalEntry createOrReplaceJournalEntry(PayrollLedger ledger) {
        Account payrollExpense = findRoleAccount(PayrollAccountingRole.PAYROLL_EXPENSE);
        Account withholdingPayable = findRoleAccount(PayrollAccountingRole.WITHHOLDING_PAYABLE);
        Account salaryPayable = findRoleAccount(PayrollAccountingRole.SALARY_PAYABLE);

        JournalEntry journalEntry = JournalEntry.builder()
                .journalNo(createJournalNo(ledger))
                .transactionDate(ledger.getPaymentDate())
                .description(ledger.getLedgerName() + " 자동 분개")
                .status("DRAFT")
                .sourceType(SOURCE_TYPE)
                .sourceId(ledger.getId())
                .createdBy(ledger.getCreatedBy())
                .build();

        journalEntry.addLine(JournalLine.builder()
                .account(payrollExpense)
                .debitAmount(ledger.getGrossAmount())
                .creditAmount(ZERO)
                .description("급여/상여 지급총액")
                .referenceType(SOURCE_TYPE)
                .referenceId(ledger.getId())
                .build());

        if (ledger.getDeductionAmount().compareTo(ZERO) > 0) {
            journalEntry.addLine(JournalLine.builder()
                    .account(withholdingPayable)
                    .debitAmount(ZERO)
                    .creditAmount(ledger.getDeductionAmount())
                    .description("4대보험 및 원천세 예수금")
                    .referenceType(SOURCE_TYPE)
                    .referenceId(ledger.getId())
                    .build());
        }

        journalEntry.addLine(JournalLine.builder()
                .account(salaryPayable)
                .debitAmount(ZERO)
                .creditAmount(ledger.getNetAmount())
                .description("미지급급여")
                .referenceType(SOURCE_TYPE)
                .referenceId(ledger.getId())
                .build());

        if (journalEntry.getTotalDebit().compareTo(journalEntry.getTotalCredit()) != 0) {
            throw new IllegalStateException("Payroll journal entry is not balanced.");
        }
        return journalEntryRepository.save(journalEntry);
    }

    private List<Employee> resolveTargetEmployees(PayrollLedger ledger) {
        if (ledger.getEmployeeSelectionMode() == PayrollSelectionMode.ALL) {
            return employeeRepository.findAll().stream()
                    .filter(employee -> !"TERMINATED".equalsIgnoreCase(employee.getEmploymentStatus()))
                    .collect(Collectors.toList());
        }
        return ledger.getEmployees().stream()
                .map(PayrollLedgerEmployee::getEmployee)
                .collect(Collectors.toList());
    }

    private PayrollLedger findLedger(Long ledgerId) {
        return payrollLedgerRepository.findWithEmployeesById(ledgerId)
                .orElseThrow(() -> new IllegalArgumentException("Payroll ledger not found: " + ledgerId));
    }

    private Account findRoleAccount(PayrollAccountingRole role) {
        Account account = accountRepository.findByCode(role.getDefaultAccountCode())
                .orElseThrow(() -> new IllegalStateException("Required payroll account is missing: " + role.getLabel()));
        if (!Boolean.TRUE.equals(account.getAllowPosting()) || !account.isLeaf()) {
            throw new IllegalStateException("Payroll account must be a posting leaf account: " + account.getCode());
        }
        return account;
    }

    private void validateCreateRequest(PayrollLedgerCreateRequest request) {
        if (!StringUtils.hasText(request.getAttributionYearMonth())) throw new IllegalArgumentException("attributionYearMonth is required.");
        if (request.getPayrollType() == null) throw new IllegalArgumentException("payrollType is required.");
        if (request.getTaxType() == null) throw new IllegalArgumentException("taxType is required.");
        if (request.getSettlementCycle() == null) throw new IllegalArgumentException("settlementCycle is required.");
        if (request.getTargetPeriodMode() == null) throw new IllegalArgumentException("targetPeriodMode is required.");
        if (request.getPaymentDate() == null) throw new IllegalArgumentException("paymentDate is required.");
        if (!StringUtils.hasText(request.getPaymentYearMonth())) throw new IllegalArgumentException("paymentYearMonth is required.");
        if (!StringUtils.hasText(request.getLedgerName())) throw new IllegalArgumentException("ledgerName is required.");
        if (request.getSettlementItemSelectionMode() == null) request.setSettlementItemSelectionMode(PayrollSelectionMode.ALL);
        if (request.getEmployeeSelectionMode() == null) throw new IllegalArgumentException("employeeSelectionMode is required.");
        if (request.getEmployeeSelectionMode() == PayrollSelectionMode.SELECTED && (request.getEmployeeIds() == null || request.getEmployeeIds().isEmpty())) {
            throw new IllegalArgumentException("At least one employee is required when employeeSelectionMode is SELECTED.");
        }
    }

    private PayrollSettlementItem settlementItem(PayrollSettlementItemType type) {
        return PayrollSettlementItem.builder()
                .settlementItemType(type)
                .itemName(type.getLabel())
                .status("READY")
                .build();
    }

    private PayrollLedgerEmployee targetEmployeeRow(Employee employee) {
        return PayrollLedgerEmployee.builder()
                .employee(employee)
                .employeeNoSnapshot(employee.getEmployeeNo())
                .employeeNameSnapshot(employee.getName())
                .departmentSnapshot(employee.getDepartment())
                .positionSnapshot(employee.getPosition())
                .bankAccountSnapshot("")
                .paymentStatus("READY")
                .grossAmount(ZERO)
                .deductionAmount(ZERO)
                .netAmount(ZERO)
                .build();
    }

    private PayrollItem payItem(PayrollPayItemType type, BigDecimal amount, boolean taxable, int sortOrder) {
        return PayrollItem.builder()
                .itemType(type)
                .itemName(type.getLabel())
                .amount(roundDown(amount))
                .taxable(taxable)
                .sortOrder(sortOrder)
                .build();
    }

    private PayrollDeductionItem deductionItem(PayrollDeductionType type, BigDecimal amount, int sortOrder) {
        return PayrollDeductionItem.builder()
                .deductionType(type)
                .itemName(type.getLabel())
                .amount(roundDown(amount))
                .sortOrder(sortOrder)
                .build();
    }

    private BigDecimal calculateBonus(PayrollLedger ledger, BigDecimal baseSalary) {
        if (ledger.getPayrollType() != PayrollType.BONUS && ledger.getPayrollType() != PayrollType.INCENTIVE) {
            return ZERO;
        }
        if (!StringUtils.hasText(ledger.getBonusRateOrAmount())) {
            return ledger.getPayrollType() == PayrollType.BONUS ? baseSalary.multiply(new BigDecimal("0.5")) : ZERO;
        }
        String value = ledger.getBonusRateOrAmount().trim();
        if (value.endsWith("%")) {
            BigDecimal rate = new BigDecimal(value.substring(0, value.length() - 1)).divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
            return baseSalary.multiply(rate);
        }
        return new BigDecimal(value.replace(",", ""));
    }

    private BigDecimal calculateIncomeTax(BigDecimal taxableGross) {
        BigDecimal annualSalary = taxableGross.multiply(new BigDecimal("12"));
        if (annualSalary.compareTo(new BigDecimal("14000000")) <= 0) {
            return annualSalary.multiply(new BigDecimal("0.06")).divide(new BigDecimal("12"), 0, RoundingMode.DOWN);
        }
        if (annualSalary.compareTo(new BigDecimal("50000000")) <= 0) {
            return annualSalary.multiply(new BigDecimal("0.15")).subtract(new BigDecimal("1260000")).divide(new BigDecimal("12"), 0, RoundingMode.DOWN);
        }
        return annualSalary.multiply(new BigDecimal("0.24")).subtract(new BigDecimal("5760000")).divide(new BigDecimal("12"), 0, RoundingMode.DOWN);
    }

    private BigDecimal nonNegative(BigDecimal value, String fieldName) {
        if (value == null || value.compareTo(ZERO) < 0) {
            throw new IllegalArgumentException(fieldName + " cannot be negative.");
        }
        return value;
    }

    private BigDecimal roundDown(BigDecimal value) {
        return value.setScale(0, RoundingMode.DOWN);
    }

    private String createJournalNo(PayrollLedger ledger) {
        return "JPAY-" + ledger.getPaymentDate().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + ledger.getId() + "-" + System.currentTimeMillis();
    }

    private PayrollLedgerResponse toResponse(PayrollLedger ledger, boolean includeEmployees) {
        return PayrollLedgerResponse.builder()
                .id(ledger.getId())
                .attributionYearMonth(ledger.getAttributionYearMonth())
                .payrollType(ledger.getPayrollType())
                .payrollTypeLabel(ledger.getPayrollType().getLabel())
                .taxType(ledger.getTaxType())
                .settlementCycle(ledger.getSettlementCycle())
                .targetPeriodMode(ledger.getTargetPeriodMode())
                .paymentDate(ledger.getPaymentDate())
                .paymentYearMonth(ledger.getPaymentYearMonth())
                .ledgerName(ledger.getLedgerName())
                .status(ledger.getStatus())
                .headCount(ledger.getHeadCount())
                .grossAmount(ledger.getGrossAmount())
                .deductionAmount(ledger.getDeductionAmount())
                .netAmount(ledger.getNetAmount())
                .bonusRateOrAmount(ledger.getBonusRateOrAmount())
                .preEmployeeChecked(ledger.getPreEmployeeChecked())
                .preInsuranceCalculated(ledger.getPreInsuranceCalculated())
                .preSettlementValidated(ledger.getPreSettlementValidated())
                .preAccountValidated(ledger.getPreAccountValidated())
                .createdBy(ledger.getCreatedBy())
                .createdAt(ledger.getCreatedAt())
                .journalEntryId(ledger.getJournalEntry() != null ? ledger.getJournalEntry().getId() : null)
                .settlementItems(ledger.getSettlementItems().stream().map(PayrollSettlementItem::getItemName).collect(Collectors.toList()))
                .employees(includeEmployees ? ledger.getEmployees().stream().map(this::toEmployeeResponse).collect(Collectors.toList()) : List.of())
                .build();
    }

    private PayrollLedgerEmployeeResponse toEmployeeResponse(PayrollLedgerEmployee employee) {
        return PayrollLedgerEmployeeResponse.builder()
                .id(employee.getId())
                .employeeId(employee.getEmployee().getId())
                .employeeNo(employee.getEmployeeNoSnapshot())
                .employeeName(employee.getEmployeeNameSnapshot())
                .department(employee.getDepartmentSnapshot())
                .position(employee.getPositionSnapshot())
                .bankAccount(employee.getBankAccountSnapshot())
                .paymentStatus(employee.getPaymentStatus())
                .grossAmount(employee.getGrossAmount())
                .deductionAmount(employee.getDeductionAmount())
                .netAmount(employee.getNetAmount())
                .payItems(mapPayItems(employee))
                .deductionItems(mapDeductionItems(employee))
                .build();
    }

    private List<PayrollItemResponse> mapPayItems(PayrollLedgerEmployee employee) {
        return employee.getPayItems().stream()
                .map(item -> PayrollItemResponse.builder()
                        .type(item.getItemType().name())
                        .name(item.getItemName())
                        .amount(item.getAmount())
                        .taxable(item.getTaxable())
                        .build())
                .collect(Collectors.toList());
    }

    private List<PayrollDeductionResponse> mapDeductionItems(PayrollLedgerEmployee employee) {
        return employee.getDeductionItems().stream()
                .map(item -> PayrollDeductionResponse.builder()
                        .type(item.getDeductionType().name())
                        .name(item.getItemName())
                        .amount(item.getAmount())
                        .build())
                .collect(Collectors.toList());
    }
}
