package com.dduk.service.accounting.dashboard;

import com.dduk.dto.accounting.dashboard.*;
import com.dduk.entity.accounting.payroll.PayrollLedger;
import com.dduk.entity.accounting.payroll.PayrollStatus;
import com.dduk.repository.accounting.payroll.PayrollLedgerRepository;
import com.dduk.entity.accounting.period.AccountingPeriod;
import com.dduk.entity.accounting.period.AccountingPeriodStatus;
import com.dduk.entity.accounting.period.ClosingLog;
import com.dduk.entity.accounting.period.ClosingValidationStatus;
import com.dduk.repository.accounting.period.AccountingPeriodRepository;
import com.dduk.repository.accounting.period.ClosingLogRepository;
import com.dduk.dto.accounting.report.*;
import com.dduk.service.accounting.report.TrialBalanceReportService;
import com.dduk.entity.accounting.AccountSide;
import com.dduk.entity.accounting.AccountType;
import com.dduk.entity.accounting.JournalEntry;
import com.dduk.entity.accounting.voucher.Voucher;
import com.dduk.entity.accounting.voucher.enums.VoucherStatus;
import com.dduk.repository.accounting.JournalEntryRepository;
import com.dduk.repository.accounting.voucher.VoucherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AccountingDashboardService {

    private static final List<VoucherStatus> UNAPPROVED_VOUCHER_STATUSES = List.of(VoucherStatus.DRAFT, VoucherStatus.REQUESTED);
    private static final List<VoucherStatus> POSTED_OR_FINAL_VOUCHER_STATUSES = List.of(VoucherStatus.POSTED, VoucherStatus.CANCELLED);
    private static final List<PayrollStatus> PAYROLL_UNPAID_STATUSES = List.of(PayrollStatus.READY, PayrollStatus.CALCULATED, PayrollStatus.CONFIRMED);
    private static final List<PayrollStatus> PAYROLL_FINAL_STATUSES = List.of(PayrollStatus.POSTED, PayrollStatus.CLOSED, PayrollStatus.CANCELLED);

    private final JournalEntryRepository journalEntryRepository;
    private final VoucherRepository voucherRepository;
    private final AccountingPeriodRepository accountingPeriodRepository;
    private final TrialBalanceReportService trialBalanceReportService;
    private final PayrollLedgerRepository payrollLedgerRepository;
    private final ClosingLogRepository closingLogRepository;

    @Transactional(readOnly = true)
    public AccountingDashboardResponse getDashboard(Integer fiscalYear, Integer fiscalMonth) {
        YearMonth targetMonth = resolveYearMonth(fiscalYear, fiscalMonth);
        LocalDate startDate = targetMonth.atDay(1);
        LocalDate endDate = targetMonth.atEndOfMonth();
        Optional<AccountingPeriod> period = accountingPeriodRepository.findByFiscalYearAndFiscalMonth(targetMonth.getYear(), targetMonth.getMonthValue());

        DashboardKpiSummary kpi = getKpiSummary(targetMonth.getYear(), targetMonth.getMonthValue());
        VoucherDashboardSummary vouchers = getVoucherSummary(targetMonth.getYear(), targetMonth.getMonthValue());
        PeriodDashboardSummary periodSummary = getPeriodSummary(targetMonth.getYear(), targetMonth.getMonthValue());
        TrialBalanceDashboardSummary trialBalance = getTrialBalanceSummary(targetMonth.getYear(), targetMonth.getMonthValue());
        PayrollDashboardSummary payroll = getPayrollSummary(targetMonth.getYear(), targetMonth.getMonthValue());

        return AccountingDashboardResponse.builder()
                .generatedAt(LocalDateTime.now())
                .kpiSummary(kpi)
                .voucherSummary(vouchers)
                .periodSummary(periodSummary)
                .trialBalanceSummary(trialBalance)
                .payrollSummary(payroll)
                .cashFlowSummary(getCashFlowSummary(targetMonth.getYear(), targetMonth.getMonthValue()))
                .financialTrends(getFinancialTrends(targetMonth.getYear(), targetMonth.getMonthValue(), 6))
                .accountComposition(getAccountComposition(targetMonth.getYear(), targetMonth.getMonthValue()))
                .alerts(buildAlerts(period, vouchers, periodSummary, trialBalance, payroll))
                .recentActivities(getRecentActivities())
                .build();
    }

    @Transactional(readOnly = true)
    public DashboardKpiSummary getKpiSummary(Integer fiscalYear, Integer fiscalMonth) {
        YearMonth targetMonth = resolveYearMonth(fiscalYear, fiscalMonth);
        YearMonth previousMonth = targetMonth.minusMonths(1);
        Map<AccountType, BigDecimal> current = aggregateByType(targetMonth.atDay(1), targetMonth.atEndOfMonth());
        Map<AccountType, BigDecimal> previous = aggregateByType(previousMonth.atDay(1), previousMonth.atEndOfMonth());
        Map<AccountType, BigDecimal> cumulativeCurrent = aggregateCumulativeByType(targetMonth.atEndOfMonth());
        
        BigDecimal revenue = current.getOrDefault(AccountType.REVENUE, BigDecimal.ZERO);
        BigDecimal expense = current.getOrDefault(AccountType.EXPENSE, BigDecimal.ZERO);
        BigDecimal assets = cumulativeCurrent.getOrDefault(AccountType.ASSET, BigDecimal.ZERO);
        BigDecimal liabilities = cumulativeCurrent.getOrDefault(AccountType.LIABILITY, BigDecimal.ZERO);
        return DashboardKpiSummary.builder()
                .monthlyRevenue(revenue)
                .monthlyRevenueChangeRate(changeRate(revenue, previous.getOrDefault(AccountType.REVENUE, BigDecimal.ZERO)))
                .monthlyExpense(expense)
                .monthlyExpenseChangeRate(changeRate(expense, previous.getOrDefault(AccountType.EXPENSE, BigDecimal.ZERO)))
                .operatingIncome(revenue.subtract(expense))
                .netIncome(revenue.subtract(expense))
                .totalAssets(assets)
                .totalLiabilities(liabilities)
                .unapprovedVoucherCount(voucherRepository.countByVoucherDateBetweenAndStatusIn(targetMonth.atDay(1), targetMonth.atEndOfMonth(), UNAPPROVED_VOUCHER_STATUSES))
                .monthlyClosingStatus(accountingPeriodRepository.findByFiscalYearAndFiscalMonth(targetMonth.getYear(), targetMonth.getMonthValue())
                        .map(AccountingPeriod::getStatus)
                        .map(Enum::name)
                        .orElse("NOT_CREATED"))
                .build();
    }

    @Transactional(readOnly = true)
    public VoucherDashboardSummary getVoucherSummary(Integer fiscalYear, Integer fiscalMonth) {
        YearMonth targetMonth = resolveYearMonth(fiscalYear, fiscalMonth);
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (Object[] row : voucherRepository.countStatusByVoucherDateBetween(targetMonth.atDay(1), targetMonth.atEndOfMonth())) {
            statusCounts.put(((VoucherStatus) row[0]).name(), ((Number) row[1]).longValue());
        }
        return VoucherDashboardSummary.builder()
                .todayVoucherCount(voucherRepository.countByVoucherDate(LocalDate.now()))
                .draftCount(statusCounts.getOrDefault(VoucherStatus.DRAFT.name(), 0L))
                .pendingApprovalCount(statusCounts.getOrDefault(VoucherStatus.REQUESTED.name(), 0L))
                .approvedCount(statusCounts.getOrDefault(VoucherStatus.APPROVED.name(), 0L))
                .postedCount(statusCounts.getOrDefault(VoucherStatus.POSTED.name(), 0L))
                .cancelledCount(statusCounts.getOrDefault(VoucherStatus.CANCELLED.name(), 0L))
                .statusCounts(statusCounts)
                .build();
    }

    @Transactional(readOnly = true)
    public List<FinancialTrendResponse> getFinancialTrends(Integer fiscalYear, Integer fiscalMonth) {
        return getFinancialTrends(fiscalYear, fiscalMonth, 6);
    }

    @Transactional(readOnly = true)
    public PeriodDashboardSummary getPeriodSummary(Integer fiscalYear, Integer fiscalMonth) {
        YearMonth targetMonth = resolveYearMonth(fiscalYear, fiscalMonth);
        Optional<AccountingPeriod> optionalPeriod = accountingPeriodRepository.findByFiscalYearAndFiscalMonth(targetMonth.getYear(), targetMonth.getMonthValue());
        Object[] totals = journalEntryRepository.sumEntryTotalsByFiscalPeriod(targetMonth.getYear(), targetMonth.getMonthValue());
        BigDecimal debit = decimalAt(totals, 0);
        BigDecimal credit = decimalAt(totals, 1);
        long unposted = voucherRepository.countByVoucherDateBetweenAndStatusNotIn(targetMonth.atDay(1), targetMonth.atEndOfMonth(), POSTED_OR_FINAL_VOUCHER_STATUSES)
                + journalEntryRepository.countUnpostedByFiscalPeriod(targetMonth.getYear(), targetMonth.getMonthValue());
        ClosingValidationStatus validationStatus = resolveValidationStatus(targetMonth.getYear(), targetMonth.getMonthValue(), debit, credit, unposted);
        return PeriodDashboardSummary.builder()
                .currentPeriod(periodKey(targetMonth))
                .fiscalYear(targetMonth.getYear())
                .fiscalMonth(targetMonth.getMonthValue())
                .status(optionalPeriod.map(AccountingPeriod::getStatus).orElse(null))
                .validationStatus(validationStatus)
                .unpostedVoucherCount(unposted)
                .balanced(debit.compareTo(credit) == 0)
                .totalDebit(debit)
                .totalCredit(credit)
                .periodFound(optionalPeriod.isPresent())
                .build();
    }

    @Transactional(readOnly = true)
    public List<AccountingAlertResponse> getAlerts(Integer fiscalYear, Integer fiscalMonth) {
        YearMonth targetMonth = resolveYearMonth(fiscalYear, fiscalMonth);
        VoucherDashboardSummary vouchers = getVoucherSummary(fiscalYear, fiscalMonth);
        PeriodDashboardSummary period = getPeriodSummary(fiscalYear, fiscalMonth);
        TrialBalanceDashboardSummary trialBalance = getTrialBalanceSummary(fiscalYear, fiscalMonth);
        PayrollDashboardSummary payroll = getPayrollSummary(fiscalYear, fiscalMonth);
        Optional<AccountingPeriod> accountingPeriod = accountingPeriodRepository.findByFiscalYearAndFiscalMonth(targetMonth.getYear(), targetMonth.getMonthValue());
        return buildAlerts(accountingPeriod, vouchers, period, trialBalance, payroll);
    }

    @Transactional(readOnly = true)
    public List<RecentAccountingActivityResponse> getRecentActivities() {
        List<RecentAccountingActivityResponse> activities = new ArrayList<>();
        for (Voucher voucher : voucherRepository.findTop10ByOrderByUpdatedAtDesc()) {
            activities.add(RecentAccountingActivityResponse.builder()
                    .activityType("VOUCHER")
                    .target(voucher.getVoucherNo())
                    .actor(defaultActor(voucher.getCreatedBy()))
                    .activityAt(voucher.getUpdatedAt())
                    .status(voucher.getStatus().name())
                    .build());
        }
        for (JournalEntry entry : journalEntryRepository.findTop10ByOrderByCreatedAtDesc()) {
            activities.add(RecentAccountingActivityResponse.builder()
                    .activityType("JOURNAL")
                    .target(entry.getJournalNo())
                    .actor(defaultActor(entry.getCreatedBy()))
                    .activityAt(entry.getUpdatedAt())
                    .status(entry.getStatus())
                    .build());
        }
        for (PayrollLedger ledger : payrollLedgerRepository.findTop10ByOrderByUpdatedAtDesc()) {
            activities.add(RecentAccountingActivityResponse.builder()
                    .activityType("PAYROLL")
                    .target(ledger.getLedgerName())
                    .actor(defaultActor(ledger.getCreatedBy()))
                    .activityAt(ledger.getUpdatedAt())
                    .status(ledger.getStatus().name())
                    .build());
        }
        for (ClosingLog log : closingLogRepository.findTop10ByOrderByActionAtDesc()) {
            activities.add(RecentAccountingActivityResponse.builder()
                    .activityType("CLOSING")
                    .target(log.getAccountingPeriod().getPeriodKey())
                    .actor(defaultActor(log.getActor()))
                    .activityAt(log.getActionAt())
                    .status(log.getActionType().name())
                    .build());
        }
        return activities.stream()
                .sorted(Comparator.comparing(RecentAccountingActivityResponse::getActivityAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(10)
                .toList();
    }

    private TrialBalanceDashboardSummary getTrialBalanceSummary(Integer fiscalYear, Integer fiscalMonth) {
        YearMonth targetMonth = resolveYearMonth(fiscalYear, fiscalMonth);
        TrialBalanceSearchCondition condition = new TrialBalanceSearchCondition();
        condition.setStartDate(targetMonth.atDay(1));
        condition.setEndDate(targetMonth.atEndOfMonth());
        condition.setAccountLevel(AccountLevelFilter.ACCOUNT);
        condition.setIncludeZeroBalance(false);
        TrialBalanceResponse response = trialBalanceReportService.getTrialBalance(condition);
        TrialBalanceSummary summary = response.getSummary();
        List<TrialBalanceMajorAccountResponse> majorAccounts = response.getRows().stream()
                .map(row -> new TrialBalanceMajorAccountResponse(row.getAccountCode(), row.getAccountName(), row.getAccountType(), closingBalance(row)))
                .filter(row -> row.getClosingBalance().signum() != 0)
                .sorted(Comparator.comparing(TrialBalanceMajorAccountResponse::getClosingBalance).reversed())
                .limit(5)
                .toList();
        return TrialBalanceDashboardSummary.builder()
                .totalDebit(summary.getClosingDebitTotal())
                .totalCredit(summary.getClosingCreditTotal())
                .balanced(summary.isClosingBalanced())
                .majorAccounts(majorAccounts)
                .build();
    }

    private PayrollDashboardSummary getPayrollSummary(Integer fiscalYear, Integer fiscalMonth) {
        YearMonth targetMonth = resolveYearMonth(fiscalYear, fiscalMonth);
        String yearMonth = periodKey(targetMonth);
        BigDecimal monthlyAmount = payrollLedgerRepository.sumNetAmountByPaymentYearMonth(yearMonth);
        BigDecimal unpaidAmount = payrollLedgerRepository.sumNetAmountByPaymentYearMonthAndStatusIn(yearMonth, PAYROLL_UNPAID_STATUSES);
        long calculatedCount = payrollLedgerRepository.countByPaymentYearMonthAndStatus(yearMonth, PayrollStatus.CALCULATED);
        long unsettledCount = payrollLedgerRepository.countByPaymentYearMonthAndStatusIn(yearMonth, List.of(PayrollStatus.DRAFT, PayrollStatus.READY, PayrollStatus.CALCULATED, PayrollStatus.CONFIRMED));
        long openCount = payrollLedgerRepository.countOpenLedgersByPaymentYearMonth(yearMonth, PAYROLL_FINAL_STATUSES);
        return PayrollDashboardSummary.builder()
                .targetYearMonth(yearMonth)
                .monthlyPayrollAmount(monthlyAmount)
                .calculatedCount(calculatedCount)
                .unsettledCount(unsettledCount)
                .unpaidPayrollAmount(unpaidAmount)
                .nextPaymentDate(payrollLedgerRepository.findNextPaymentDate(yearMonth, PAYROLL_UNPAID_STATUSES, LocalDate.now()).orElse(null))
                .calculationCompleted(openCount == 0 && monthlyAmount.signum() > 0)
                .build();
    }

    private CashFlowSummary getCashFlowSummary(Integer fiscalYear, Integer fiscalMonth) {
        YearMonth targetMonth = resolveYearMonth(fiscalYear, fiscalMonth);
        Object[] row = journalEntryRepository.aggregateCashFlowBetweenDates(targetMonth.atDay(1), targetMonth.atEndOfMonth());
        BigDecimal inflow = decimalAt(row, 0);
        BigDecimal outflow = decimalAt(row, 1);
        return CashFlowSummary.builder()
                .cashInflow(inflow)
                .cashOutflow(outflow)
                .netCashFlow(inflow.subtract(outflow))
                .basis("현금/예금 계정(AccountType ASSET, 111*, 1001, 1002, 현금/예금 명칭) 기준")
                .build();
    }

    private List<FinancialTrendResponse> getFinancialTrends(Integer fiscalYear, Integer fiscalMonth, int months) {
        YearMonth targetMonth = resolveYearMonth(fiscalYear, fiscalMonth);
        YearMonth startMonth = targetMonth.minusMonths(Math.max(1, months) - 1L);
        Map<String, Map<AccountType, BigDecimal>> monthly = new LinkedHashMap<>();
        for (int index = 0; index < months; index++) {
            monthly.put(periodKey(startMonth.plusMonths(index)), new EnumMap<>(AccountType.class));
        }
        for (Object[] row : journalEntryRepository.aggregatePostedMonthlyByAccountType(startMonth.atDay(1), targetMonth.atEndOfMonth())) {
            String key = String.format("%d-%02d", ((Number) row[0]).intValue(), ((Number) row[1]).intValue());
            AccountType type = (AccountType) row[2];
            monthly.computeIfAbsent(key, ignored -> new EnumMap<>(AccountType.class))
                    .put(type, signedAmount(type, decimalAt(row, 3), decimalAt(row, 4)));
        }
        return monthly.entrySet().stream()
                .filter(entry -> {
                    String[] parts = entry.getKey().split("-");
                    int y = Integer.parseInt(parts[0]);
                    int m = Integer.parseInt(parts[1]);
                    return accountingPeriodRepository.findByFiscalYearAndFiscalMonth(y, m)
                            .map(p -> p.getStatus() == AccountingPeriodStatus.CLOSED)
                            .orElse(false);
                })
                .map(entry -> {
                    BigDecimal revenue = entry.getValue().getOrDefault(AccountType.REVENUE, BigDecimal.ZERO);
                    BigDecimal expense = entry.getValue().getOrDefault(AccountType.EXPENSE, BigDecimal.ZERO);
                    return FinancialTrendResponse.builder()
                            .period(entry.getKey())
                            .revenue(revenue)
                            .expense(expense)
                            .operatingIncome(revenue.subtract(expense))
                            .netIncome(revenue.subtract(expense))
                            .build();
                })
                .toList();
    }

    private List<AccountCompositionResponse> getAccountComposition(Integer fiscalYear, Integer fiscalMonth) {
        YearMonth targetMonth = resolveYearMonth(fiscalYear, fiscalMonth);
        Map<AccountType, BigDecimal> amounts = aggregateCumulativeByType(targetMonth.atEndOfMonth());
        BigDecimal asset = amounts.getOrDefault(AccountType.ASSET, BigDecimal.ZERO).abs();
        BigDecimal liability = amounts.getOrDefault(AccountType.LIABILITY, BigDecimal.ZERO).abs();
        BigDecimal equity = amounts.getOrDefault(AccountType.EQUITY, BigDecimal.ZERO).abs();
        BigDecimal total = asset.add(liability).add(equity);
        return List.of(
                new AccountCompositionResponse(AccountType.ASSET, "자산", asset, ratio(asset, total)),
                new AccountCompositionResponse(AccountType.LIABILITY, "부채", liability, ratio(liability, total)),
                new AccountCompositionResponse(AccountType.EQUITY, "자본", equity, ratio(equity, total))
        );
    }

    private Map<AccountType, BigDecimal> aggregateByType(LocalDate startDate, LocalDate endDate) {
        Map<AccountType, BigDecimal> result = new EnumMap<>(AccountType.class);
        for (Object[] row : journalEntryRepository.aggregatePostedByAccountTypeBetweenDates(startDate, endDate)) {
            AccountType type = (AccountType) row[0];
            result.put(type, signedAmount(type, decimalAt(row, 1), decimalAt(row, 2)));
        }
        return result;
    }

    private Map<AccountType, BigDecimal> aggregateCumulativeByType(LocalDate endDate) {
        Map<AccountType, BigDecimal> result = new EnumMap<>(AccountType.class);
        for (Object[] row : journalEntryRepository.aggregatePostedByAccountTypeBeforeOrOnDate(endDate)) {
            AccountType type = (AccountType) row[0];
            result.put(type, signedAmount(type, decimalAt(row, 1), decimalAt(row, 2)));
        }
        return result;
    }

    private List<AccountingAlertResponse> buildAlerts(Optional<AccountingPeriod> period, VoucherDashboardSummary vouchers,
                                                      PeriodDashboardSummary periodSummary, TrialBalanceDashboardSummary trialBalance,
                                                      PayrollDashboardSummary payroll) {
        List<AccountingAlertResponse> alerts = new ArrayList<>();
        if (period.isEmpty()) {
            alerts.add(alert(AccountingAlertSeverity.ERROR, "PERIOD", "회계기간 미생성", periodSummary.getCurrentPeriod() + " 회계기간을 생성해야 마감 통제가 가능합니다.", "월 마감 이동", "../../pages/hr/accounting/monthly_closing.html"));
        } else if (period.get().getStatus() == AccountingPeriodStatus.PRE_CLOSING) {
            alerts.add(alert(AccountingAlertSeverity.WARNING, "PERIOD", "월 마감 사전 검증 중", "현재 회계기간이 PRE_CLOSING 상태입니다. 미게시 전표와 검증 결과를 확인하세요.", "월 마감 검증", "../../pages/hr/accounting/monthly_closing.html"));
        }
        long approvalWaiting = vouchers.getDraftCount() + vouchers.getPendingApprovalCount();
        if (approvalWaiting > 0) {
            alerts.add(alert(AccountingAlertSeverity.WARNING, "VOUCHER", "승인 대기 전표 존재", approvalWaiting + "건의 전표가 승인 또는 확정 대기 상태입니다.", "전표 관리", "../../pages/hr/accounting/voucher_management.html"));
        }
        if (!periodSummary.isBalanced() || !trialBalance.isBalanced()) {
            alerts.add(alert(AccountingAlertSeverity.CRITICAL, "TRIAL_BALANCE", "차변/대변 불일치", "JournalEntry 또는 Trial Balance 합계가 일치하지 않습니다.", "시산표 조회", "../../pages/hr/accounting/trial_balance.html"));
        }
        if (periodSummary.getUnpostedVoucherCount() > 0) {
            alerts.add(alert(AccountingAlertSeverity.ERROR, "LEDGER", "미게시 전표 존재", periodSummary.getUnpostedVoucherCount() + "건이 Ledger 반영 전 상태입니다.", "전표 확인", "../../pages/hr/accounting/voucher_management.html"));
        }
        if (payroll.getUnsettledCount() > 0) {
            alerts.add(alert(AccountingAlertSeverity.WARNING, "PAYROLL", "미정산 급여 존재", payroll.getUnsettledCount() + "건의 급여대장이 마감 전 처리 대상입니다.", "급여 관리", "../../pages/hr/accounting/payroll_management.html"));
        }
        alerts.add(alert(AccountingAlertSeverity.INFO, "TAX", "부가세 신고 대상 확인", "매출/매입 전표의 부가세 신고 대상 검증 구조가 필요합니다.", "회계 리포트", "../../pages/hr/accounting/reports.html"));
        if (alerts.size() == 1) {
            alerts.add(alert(AccountingAlertSeverity.INFO, "CONTROL", "회계 통제 정상", "현재 대시보드 기준 주요 차단 항목이 없습니다.", "시산표 조회", "../../pages/hr/accounting/trial_balance.html"));
        }
        return alerts;
    }

    private ClosingValidationStatus resolveValidationStatus(Integer fiscalYear, Integer fiscalMonth, BigDecimal debit, BigDecimal credit, long unposted) {
        if (debit.compareTo(credit) != 0 || journalEntryRepository.countUnbalancedByFiscalPeriod(fiscalYear, fiscalMonth) > 0) {
            return ClosingValidationStatus.ERROR;
        }
        return unposted > 0 ? ClosingValidationStatus.WARNING : ClosingValidationStatus.SUCCESS;
    }

    private BigDecimal signedAmount(AccountType type, BigDecimal debit, BigDecimal credit) {
        AccountSide normalSide = (type == AccountType.ASSET || type == AccountType.EXPENSE) ? AccountSide.DEBIT : AccountSide.CREDIT;
        return normalSide == AccountSide.DEBIT ? debit.subtract(credit) : credit.subtract(debit);
    }

    private BigDecimal closingBalance(TrialBalanceRow row) {
        return row.getNormalBalance() == AccountSide.DEBIT
                ? row.getClosingDebit().subtract(row.getClosingCredit())
                : row.getClosingCredit().subtract(row.getClosingDebit());
    }

    private AccountingAlertResponse alert(AccountingAlertSeverity severity, String category, String title, String message, String actionLabel, String actionUrl) {
        return AccountingAlertResponse.builder()
                .severity(severity)
                .category(category)
                .title(title)
                .message(message)
                .actionLabel(actionLabel)
                .actionUrl(actionUrl)
                .build();
    }

    private YearMonth resolveYearMonth(Integer fiscalYear, Integer fiscalMonth) {
        LocalDate today = LocalDate.now();
        int year = fiscalYear == null ? today.getYear() : fiscalYear;
        int month = fiscalMonth == null ? today.getMonthValue() : fiscalMonth;
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("fiscalMonth must be between 1 and 12.");
        }
        return YearMonth.of(year, month);
    }

    private String periodKey(YearMonth yearMonth) {
        return yearMonth.getYear() + "-" + String.format("%02d", yearMonth.getMonthValue());
    }

    private BigDecimal changeRate(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous.abs(), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(BigDecimal value, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return value.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal decimalAt(Object[] row, int index) {
        if (row == null || row.length <= index || row[index] == null) {
            return BigDecimal.ZERO;
        }
        try {
            if (row[index] instanceof BigDecimal value) {
                return value;
            }
            if (row[index] instanceof Number number) {
                // Double, Long, Integer 등은 안전하게 doubleValue를 통해 변환
                return BigDecimal.valueOf(number.doubleValue());
            }
            String strValue = row[index].toString().trim();
            if (strValue.isEmpty() || "null".equalsIgnoreCase(strValue)) {
                return BigDecimal.ZERO;
            }
            return new BigDecimal(strValue);
        } catch (Exception e) {
            // 지수 표기법 오류나 드라이버 파싱 장애 시 안전하게 0.00으로 폴백하여 대시보드 마비 차단
            return BigDecimal.ZERO;
        }
    }

    private String defaultActor(String actor) {
        return actor == null || actor.isBlank() ? "SYSTEM" : actor;
    }
}
