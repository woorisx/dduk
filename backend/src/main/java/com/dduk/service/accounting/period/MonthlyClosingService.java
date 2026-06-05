package com.dduk.service.accounting.period;

import com.dduk.entity.accounting.payroll.PayrollStatus;
import com.dduk.repository.accounting.payroll.PayrollLedgerRepository;
import com.dduk.dto.accounting.period.*;
import com.dduk.entity.accounting.period.*;
import com.dduk.repository.accounting.period.AccountingPeriodRepository;
import com.dduk.repository.accounting.period.ClosingLogRepository;
import com.dduk.entity.accounting.voucher.enums.VoucherStatus;
import com.dduk.repository.accounting.JournalEntryRepository;
import com.dduk.repository.accounting.voucher.VoucherRepository;
import com.dduk.repository.inventory.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MonthlyClosingService {

    private static final List<AccountingPeriodStatus> LOCKED_STATUSES = List.of(AccountingPeriodStatus.CLOSED, AccountingPeriodStatus.ARCHIVED);
    private static final List<VoucherStatus> APPROVED_OR_FINAL_VOUCHER_STATUSES = List.of(VoucherStatus.APPROVED, VoucherStatus.POSTED, VoucherStatus.CANCELLED);
    private static final List<VoucherStatus> POSTED_OR_FINAL_VOUCHER_STATUSES = List.of(VoucherStatus.POSTED, VoucherStatus.CANCELLED);
    private static final List<PayrollStatus> PAYROLL_FINAL_STATUSES = List.of(PayrollStatus.CONFIRMED, PayrollStatus.POSTED, PayrollStatus.CLOSED, PayrollStatus.CANCELLED);

    private final AccountingPeriodRepository accountingPeriodRepository;
    private final ClosingLogRepository closingLogRepository;
    private final VoucherRepository voucherRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final PayrollLedgerRepository payrollLedgerRepository;
    private final InventoryRepository inventoryRepository;

    @Transactional
    public AccountingPeriodResponse createPeriod(AccountingPeriodCreateRequest request) {
        validateCreateRequest(request);
        if (accountingPeriodRepository.existsByFiscalYearAndFiscalMonth(request.getFiscalYear(), request.getFiscalMonth())) {
            throw new IllegalArgumentException("Accounting period already exists: " + periodKey(request.getFiscalYear(), request.getFiscalMonth()));
        }
        assertNoLockedOverlap(request.getStartDate(), request.getEndDate(), null);

        AccountingPeriod period = AccountingPeriod.builder()
                .fiscalYear(request.getFiscalYear())
                .fiscalMonth(request.getFiscalMonth())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(AccountingPeriodStatus.OPEN)
                .build();
        AccountingPeriod saved = accountingPeriodRepository.save(period);
        log(saved, ClosingActionType.PERIOD_CREATED, null, saved.getStatus(), actor(request.getCreatedBy()), null, "Accounting period created.");
        return toResponse(saved);
    }

    @Transactional
    public List<AccountingPeriodResponse> createYearPeriods(AccountingYearCreateRequest request) {
        if (request.getFiscalYear() == null) {
            throw new IllegalArgumentException("fiscalYear is required.");
        }
        List<AccountingPeriodResponse> created = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            if (accountingPeriodRepository.existsByFiscalYearAndFiscalMonth(request.getFiscalYear(), month)) {
                continue;
            }
            YearMonth yearMonth = YearMonth.of(request.getFiscalYear(), month);
            AccountingPeriod period = accountingPeriodRepository.save(AccountingPeriod.builder()
                    .fiscalYear(request.getFiscalYear())
                    .fiscalMonth(month)
                    .startDate(yearMonth.atDay(1))
                    .endDate(yearMonth.atEndOfMonth())
                    .status(AccountingPeriodStatus.OPEN)
                    .build());
            log(period, ClosingActionType.YEAR_PERIODS_CREATED, null, period.getStatus(), actor(request.getCreatedBy()), null, "Year accounting period generated.");
            created.add(toResponse(period));
        }
        return created;
    }

    @Transactional(readOnly = true)
    public List<AccountingPeriodResponse> getPeriods(Integer fiscalYear) {
        List<AccountingPeriod> periods = fiscalYear == null
                ? accountingPeriodRepository.findAllByOrderByFiscalYearDescFiscalMonthDesc()
                : accountingPeriodRepository.findByFiscalYearOrderByFiscalMonthAsc(fiscalYear);
        return periods.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AccountingPeriodResponse getStatus(Integer fiscalYear, Integer fiscalMonth) {
        return toResponse(findPeriod(fiscalYear, fiscalMonth));
    }

    @Transactional(readOnly = true)
    public ClosingSummaryResponse getSummary(Integer fiscalYear, Integer fiscalMonth) {
        YearMonth targetMonth = resolveYearMonth(fiscalYear, fiscalMonth);
        AccountingPeriod period = accountingPeriodRepository.findByFiscalYearAndFiscalMonth(targetMonth.getYear(), targetMonth.getMonthValue())
                .orElse(null);
        if (period == null) {
            return ClosingSummaryResponse.builder()
                    .currentPeriod(periodKey(targetMonth.getYear(), targetMonth.getMonthValue()))
                    .status(AccountingPeriodStatus.OPEN)
                    .unapprovedVoucherCount(voucherRepository.countByVoucherDateBetweenAndStatusNotIn(targetMonth.atDay(1), targetMonth.atEndOfMonth(), APPROVED_OR_FINAL_VOUCHER_STATUSES))
                    .unpostedVoucherCount(voucherRepository.countByVoucherDateBetweenAndStatusNotIn(targetMonth.atDay(1), targetMonth.atEndOfMonth(), POSTED_OR_FINAL_VOUCHER_STATUSES)
                            + journalEntryRepository.countUnpostedByFiscalPeriod(targetMonth.getYear(), targetMonth.getMonthValue()))
                    .totalDebit(BigDecimal.ZERO)
                    .totalCredit(BigDecimal.ZERO)
                    .balanced(true)
                    .mismatchStatus("NOT_CREATED")
                    .build();
        }
        PeriodAggregation aggregation = aggregate(period);
        long unapproved = voucherRepository.countByVoucherDateBetweenAndStatusNotIn(period.getStartDate(), period.getEndDate(), APPROVED_OR_FINAL_VOUCHER_STATUSES);
        long unposted = voucherRepository.countByVoucherDateBetweenAndStatusNotIn(period.getStartDate(), period.getEndDate(), POSTED_OR_FINAL_VOUCHER_STATUSES)
                + journalEntryRepository.countUnpostedByFiscalPeriod(period.getFiscalYear(), period.getFiscalMonth());
        boolean balanced = aggregation.totalDebit().compareTo(aggregation.totalCredit()) == 0;
        return ClosingSummaryResponse.builder()
                .currentPeriod(period.getPeriodKey())
                .status(period.getStatus())
                .unapprovedVoucherCount(unapproved)
                .unpostedVoucherCount(unposted)
                .totalDebit(aggregation.totalDebit())
                .totalCredit(aggregation.totalCredit())
                .balanced(balanced)
                .mismatchStatus(balanced ? "정상" : "차변/대변 불일치")
                .build();
    }

    @Transactional
    public ClosingValidationResponse validatePeriod(Integer fiscalYear, Integer fiscalMonth, ClosingActionRequest request) {
        AccountingPeriod period = findPeriod(fiscalYear, fiscalMonth);
        ClosingValidationResponse response = buildValidation(period);
        log(period,
                response.isClosable() ? ClosingActionType.VALIDATION_SUCCESS : ClosingActionType.VALIDATION_FAILED,
                period.getStatus(),
                period.getStatus(),
                actor(request == null ? null : request.getActor()),
                request == null ? null : request.getIpAddress(),
                response.isClosable() ? "Closing validation passed." : "Closing validation failed.");
        return response;
    }

    @Transactional
    public AccountingPeriodResponse closePeriod(Integer fiscalYear, Integer fiscalMonth, ClosingActionRequest request) {
        AccountingPeriod period = findPeriod(fiscalYear, fiscalMonth);
        ClosingValidationResponse validation = buildValidation(period);
        if (!validation.isClosable() && (request == null || !Boolean.TRUE.equals(request.getForce()))) {
            log(period, ClosingActionType.VALIDATION_FAILED, period.getStatus(), period.getStatus(), actor(null), null, "Closing blocked by validation errors.");
            throw new IllegalStateException("Closing validation must pass before monthly closing.");
        }

        AccountingPeriodStatus before = period.getStatus();
        period.markPreClosing();
        log(period, ClosingActionType.PRE_CLOSING_STARTED, before, period.getStatus(), actor(request == null ? null : request.getActor()), request == null ? null : request.getIpAddress(), "Pre-closing control started.");

        AccountingPeriodStatus preClosing = period.getStatus();
        period.close(actor(request == null ? null : request.getActor()));
        log(period, ClosingActionType.MONTH_CLOSED, preClosing, period.getStatus(), actor(request == null ? null : request.getActor()), request == null ? null : request.getIpAddress(), "Monthly closing completed. Vouchers, journals, ledgers and payroll are controlled by accounting period status.");
        return toResponse(period);
    }

    @Transactional
    public AccountingPeriodResponse reopenPeriod(Integer fiscalYear, Integer fiscalMonth, ClosingActionRequest request) {
        AccountingPeriod period = findPeriod(fiscalYear, fiscalMonth);
        AccountingPeriodStatus before = period.getStatus();
        period.reopen(actor(request == null ? null : request.getActor()));
        log(period, ClosingActionType.REOPENED, before, period.getStatus(), actor(request == null ? null : request.getActor()), request == null ? null : request.getIpAddress(), "Accounting period reopened for controlled corrections.");
        return toResponse(period);
    }

    @Transactional(readOnly = true)
    public List<ClosingLogResponse> getLogs(Long periodId) {
        AccountingPeriod period = accountingPeriodRepository.findById(periodId)
                .orElseThrow(() -> new IllegalArgumentException("Accounting period not found: " + periodId));
        return closingLogRepository.findByAccountingPeriodIdOrderByActionAtDesc(period.getId())
                .stream()
                .map(ClosingLogResponse::from)
                .toList();
    }

    public void assertPeriodMutable(LocalDate date) {
        accountingPeriodRepository.findByFiscalYearAndFiscalMonth(date.getYear(), date.getMonthValue())
                .filter(AccountingPeriod::isClosed)
                .ifPresent(period -> {
                    throw new IllegalStateException("Closed accounting period cannot be modified: " + period.getPeriodKey());
                });
    }

    private ClosingValidationResponse buildValidation(AccountingPeriod period) {
        List<ClosingValidationResult> results = new ArrayList<>();
        long draftVouchers = voucherRepository.countByVoucherDateBetweenAndStatus(period.getStartDate(), period.getEndDate(), VoucherStatus.DRAFT);
        long waitingVouchers = voucherRepository.countByVoucherDateBetweenAndStatus(period.getStartDate(), period.getEndDate(), VoucherStatus.REQUESTED);
        long unposted = voucherRepository.countByVoucherDateBetweenAndStatusNotIn(period.getStartDate(), period.getEndDate(), POSTED_OR_FINAL_VOUCHER_STATUSES)
                + journalEntryRepository.countUnpostedByFiscalPeriod(period.getFiscalYear(), period.getFiscalMonth());
        long cancelled = voucherRepository.countByVoucherDateBetweenAndStatus(period.getStartDate(), period.getEndDate(), VoucherStatus.CANCELLED)
                + journalEntryRepository.countByFiscalYearAndFiscalMonthAndStatusIn(period.getFiscalYear(), period.getFiscalMonth(), List.of("CANCELLED"));
        results.add(result("VOUCHER", draftVouchers == 0 && waitingVouchers == 0 && unposted == 0 ? ClosingValidationStatus.SUCCESS : ClosingValidationStatus.ERROR,
                "DRAFT/승인대기/미게시 전표를 확인합니다.", draftVouchers + waitingVouchers + unposted + cancelled, draftVouchers + waitingVouchers + unposted > 0));

        long unbalanced = journalEntryRepository.countUnbalancedByFiscalPeriod(period.getFiscalYear(), period.getFiscalMonth());
        long missingLines = journalEntryRepository.countEntriesWithoutLines(period.getFiscalYear(), period.getFiscalMonth());
        long negativeLines = journalEntryRepository.countNegativeLines(period.getFiscalYear(), period.getFiscalMonth());
        results.add(result("JOURNAL", unbalanced + missingLines + negativeLines == 0 ? ClosingValidationStatus.SUCCESS : ClosingValidationStatus.ERROR,
                "차변/대변 불일치, JournalLine 누락, 마이너스 금액을 검증합니다.", unbalanced + missingLines + negativeLines, unbalanced + missingLines + negativeLines > 0));

        long invalidAccountLines = journalEntryRepository.countInvalidPostingAccountLines(period.getFiscalYear(), period.getFiscalMonth());
        results.add(result("ACCOUNT", invalidAccountLines == 0 ? ClosingValidationStatus.SUCCESS : ClosingValidationStatus.ERROR,
                "전표에 사용된 비활성 계정, 말단계정 위반, posting 차단 계정을 점검합니다.", invalidAccountLines, invalidAccountLines > 0));

        long openPayroll = payrollLedgerRepository.countOpenLedgersByPaymentYearMonth(period.getPeriodKey(), PAYROLL_FINAL_STATUSES);
        results.add(result("PAYROLL", openPayroll == 0 ? ClosingValidationStatus.SUCCESS : ClosingValidationStatus.WARNING,
                "급여 미처리 또는 미확정 급여대장을 점검합니다.", openPayroll, openPayroll > 0));

        long negativeStock = inventoryRepository.countNegativeStockItems();
        results.add(result("INVENTORY", negativeStock == 0 ? ClosingValidationStatus.SUCCESS : ClosingValidationStatus.WARNING,
                "재고 도메인 연계를 위한 음수 재고/불일치 검증 확장 지점입니다.", negativeStock, negativeStock > 0));

        results.add(result("TAX", ClosingValidationStatus.WARNING,
                "부가세 확정/신고 대상 누락 검증은 세금 도메인 연결 시 활성화됩니다.", 0, false));

        ClosingValidationStatus overall = results.stream()
                .map(ClosingValidationResult::getStatus)
                .max(Comparator.comparingInt(this::severity))
                .orElse(ClosingValidationStatus.SUCCESS);
        boolean closable = results.stream().noneMatch(result -> result.getStatus() == ClosingValidationStatus.ERROR);
        return ClosingValidationResponse.builder()
                .periodKey(period.getPeriodKey())
                .overallStatus(overall)
                .closable(closable)
                .results(results)
                .build();
    }

    private AccountingPeriodResponse toResponse(AccountingPeriod period) {
        ClosingValidationStatus status = buildValidationStatusOnly(period);
        return AccountingPeriodResponse.of(period, aggregate(period), status);
    }

    private ClosingValidationStatus buildValidationStatusOnly(AccountingPeriod period) {
        if (journalEntryRepository.countUnbalancedByFiscalPeriod(period.getFiscalYear(), period.getFiscalMonth()) > 0) {
            return ClosingValidationStatus.ERROR;
        }
        long unposted = journalEntryRepository.countUnpostedByFiscalPeriod(period.getFiscalYear(), period.getFiscalMonth());
        return unposted > 0 ? ClosingValidationStatus.WARNING : ClosingValidationStatus.SUCCESS;
    }

    private PeriodAggregation aggregate(AccountingPeriod period) {
        long voucherCount = voucherRepository.countByVoucherDateBetween(period.getStartDate(), period.getEndDate());
        Object[] totals = journalEntryRepository.sumEntryTotalsByFiscalPeriod(period.getFiscalYear(), period.getFiscalMonth());
        return new PeriodAggregation(voucherCount, decimalAt(totals, 0), decimalAt(totals, 1));
    }

    private BigDecimal decimalAt(Object[] row, int index) {
        if (row != null && row.length == 1 && row[0] instanceof Object[] nestedRow) {
            return decimalAt(nestedRow, index);
        }
        if (row == null || row.length <= index || row[index] == null) {
            return BigDecimal.ZERO;
        }
        Object val = row[index];
        if (val instanceof BigDecimal) {
            return (BigDecimal) val;
        }
        if (val instanceof byte[]) {
            String str = new String((byte[]) val, java.nio.charset.StandardCharsets.UTF_8);
            return str.trim().isEmpty() ? BigDecimal.ZERO : new BigDecimal(str);
        }
        String str = val.toString().trim();
        if (str.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(str);
        } catch (NumberFormatException e) {
            System.err.println("[MonthlyClosingService] NumberFormatException for value: " + str);
            return BigDecimal.ZERO;
        }
    }

    private AccountingPeriod resolvePeriod(Integer fiscalYear, Integer fiscalMonth) {
        LocalDate today = LocalDate.now();
        return findPeriod(fiscalYear == null ? today.getYear() : fiscalYear, fiscalMonth == null ? today.getMonthValue() : fiscalMonth);
    }

    private YearMonth resolveYearMonth(Integer fiscalYear, Integer fiscalMonth) {
        LocalDate today = LocalDate.now();
        return YearMonth.of(fiscalYear == null ? today.getYear() : fiscalYear, fiscalMonth == null ? today.getMonthValue() : fiscalMonth);
    }

    private AccountingPeriod findPeriod(Integer fiscalYear, Integer fiscalMonth) {
        if (fiscalYear == null || fiscalMonth == null) {
            throw new IllegalArgumentException("fiscalYear and fiscalMonth are required.");
        }
        return accountingPeriodRepository.findByFiscalYearAndFiscalMonth(fiscalYear, fiscalMonth)
                .orElseThrow(() -> new IllegalArgumentException("Accounting period not found: " + periodKey(fiscalYear, fiscalMonth)));
    }

    private void validateCreateRequest(AccountingPeriodCreateRequest request) {
        if (request.getFiscalYear() == null) throw new IllegalArgumentException("fiscalYear is required.");
        if (request.getFiscalMonth() == null || request.getFiscalMonth() < 1 || request.getFiscalMonth() > 12) {
            throw new IllegalArgumentException("fiscalMonth must be between 1 and 12.");
        }
        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new IllegalArgumentException("startDate and endDate are required.");
        }
        if (!request.getStartDate().isBefore(request.getEndDate())) {
            throw new IllegalArgumentException("startDate must be before endDate.");
        }
    }

    private void assertNoLockedOverlap(LocalDate startDate, LocalDate endDate, Long excludeId) {
        if (accountingPeriodRepository.existsOverlappingLockedPeriod(startDate, endDate, LOCKED_STATUSES, excludeId)) {
            throw new IllegalArgumentException("Cannot create or modify an accounting period overlapping a CLOSED/ARCHIVED period.");
        }
    }

    private ClosingValidationResult result(String type, ClosingValidationStatus status, String detail, long count, boolean actionRequired) {
        return ClosingValidationResult.builder()
                .validationType(type)
                .status(status)
                .detail(detail)
                .targetCount(count)
                .actionRequired(actionRequired)
                .build();
    }

    private int severity(ClosingValidationStatus status) {
        return switch (status) {
            case SUCCESS -> 0;
            case WARNING -> 1;
            case ERROR -> 2;
        };
    }

    private void log(AccountingPeriod period, ClosingActionType actionType, AccountingPeriodStatus fromStatus, AccountingPeriodStatus toStatus,
                     String actor, String ipAddress, String message) {
        closingLogRepository.save(ClosingLog.builder()
                .accountingPeriod(period)
                .actionType(actionType)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .actor(actor)
                .ipAddress(ipAddress)
                .message(message)
                .build());
    }

    private String actor(String actor) {
        return StringUtils.hasText(actor) ? actor.trim() : "SYSTEM";
    }

    private String periodKey(Integer year, Integer month) {
        return year + "-" + String.format("%02d", month);
    }
}
