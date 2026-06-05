package com.dduk.service.accounting.report;

import com.dduk.entity.accounting.payroll.PayrollStatus;
import com.dduk.repository.accounting.payroll.PayrollLedgerRepository;
import com.dduk.dto.accounting.report.*;
import com.dduk.dto.accounting.report.analytics.*;
import com.dduk.entity.accounting.AccountSide;
import com.dduk.entity.accounting.AccountType;
import com.dduk.entity.accounting.voucher.enums.VoucherStatus;
import com.dduk.entity.accounting.voucher.enums.VoucherType;
import com.dduk.repository.accounting.JournalEntryRepository;
import com.dduk.repository.accounting.voucher.VoucherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountingReportAnalyticsService {

    private static final List<VoucherStatus> REPORT_VOUCHER_STATUSES = List.of(VoucherStatus.APPROVED, VoucherStatus.POSTED);
    private static final List<PayrollStatus> REPORT_PAYROLL_STATUSES = List.of(PayrollStatus.CALCULATED, PayrollStatus.CONFIRMED, PayrollStatus.POSTED, PayrollStatus.CLOSED);

    private final TrialBalanceReportService trialBalanceReportService;
    private final JournalEntryRepository journalEntryRepository;
    private final VoucherRepository voucherRepository;
    private final PayrollLedgerRepository payrollLedgerRepository;

    @Transactional(readOnly = true)
    public AccountingReportResponse getReport(LocalDate startDate, LocalDate endDate, AccountingReportBasis reportBasis, AccountingReportType reportType) {
        ReportRange range = normalize(startDate, endDate, reportBasis, reportType);
        TrialBalanceResponse trialBalance = trialBalance(range);
        List<AccountAnalysisRow> accountRows = accountAnalysisRows(trialBalance);
        FinancialSummaryResponse summary = financialSummary(range, accountRows);

        return AccountingReportResponse.builder()
                .startDate(range.startDate())
                .endDate(range.endDate())
                .reportBasis(range.reportBasis())
                .reportType(range.reportType())
                .generatedAt(java.time.LocalDateTime.now())
                .financialSummary(summary)
                .monthlyTrends(getMonthlyTrends(range.startDate(), range.endDate()))
                .balanceComposition(balanceComposition(summary))
                .salesAnalysis(getSalesAnalysis(range.startDate(), range.endDate()))
                .expenseAnalysis(getExpenseAnalysis(accountRows))
                .accountAnalysis(accountRows)
                .voucherFlows(getVoucherFlows(range.startDate(), range.endDate()))
                .payrollAnalysis(getPayrollAnalysis(range.startDate(), range.endDate()))
                .build();
    }

    @Transactional(readOnly = true)
    public FinancialSummaryResponse getKpiSummary(LocalDate startDate, LocalDate endDate) {
        ReportRange range = normalize(startDate, endDate, AccountingReportBasis.MONTHLY, AccountingReportType.COMPREHENSIVE);
        return financialSummary(range, accountAnalysisRows(trialBalance(range)));
    }

    @Transactional(readOnly = true)
    public List<MonthlyTrendResponse> getMonthlyTrends(LocalDate startDate, LocalDate endDate) {
        ReportRange range = normalize(startDate, endDate, AccountingReportBasis.MONTHLY, AccountingReportType.COMPREHENSIVE);
        YearMonth startMonth = YearMonth.from(range.startDate());
        YearMonth endMonth = YearMonth.from(range.endDate());
        Map<String, Map<AccountType, BigDecimal>> monthly = new LinkedHashMap<>();
        YearMonth cursor = startMonth;
        while (!cursor.isAfter(endMonth)) {
            monthly.put(periodKey(cursor), new EnumMap<>(AccountType.class));
            cursor = cursor.plusMonths(1);
        }
        for (Object[] row : journalEntryRepository.aggregatePostedMonthlyByAccountType(range.startDate(), range.endDate())) {
            String key = String.format("%d-%02d", ((Number) row[0]).intValue(), ((Number) row[1]).intValue());
            AccountType type = (AccountType) row[2];
            monthly.computeIfAbsent(key, ignored -> new EnumMap<>(AccountType.class))
                    .put(type, signedAmount(type, decimalAt(row, 3), decimalAt(row, 4)));
        }
        return monthly.entrySet().stream()
                .map(entry -> {
                    BigDecimal revenue = entry.getValue().getOrDefault(AccountType.REVENUE, BigDecimal.ZERO);
                    BigDecimal expense = entry.getValue().getOrDefault(AccountType.EXPENSE, BigDecimal.ZERO);
                    return MonthlyTrendResponse.builder()
                            .period(entry.getKey())
                            .revenue(revenue)
                            .expense(expense)
                            .operatingIncome(revenue.subtract(expense))
                            .netIncome(revenue.subtract(expense))
                            .build();
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BalanceCompositionResponse> getBalanceComposition(LocalDate startDate, LocalDate endDate) {
        return balanceComposition(getKpiSummary(startDate, endDate));
    }

    @Transactional(readOnly = true)
    public List<AccountAnalysisRow> getAccountAnalysis(LocalDate startDate, LocalDate endDate) {
        ReportRange range = normalize(startDate, endDate, AccountingReportBasis.MONTHLY, AccountingReportType.ACCOUNT);
        return accountAnalysisRows(trialBalance(range));
    }

    @Transactional(readOnly = true)
    public byte[] exportExcel(LocalDate startDate, LocalDate endDate, AccountingReportBasis reportBasis, AccountingReportType reportType) {
        AccountingReportResponse report = getReport(startDate, endDate, reportBasis, reportType);
        StringBuilder csv = new StringBuilder();
        csv.append('\uFEFF');
        csv.append("구분,값\n");
        FinancialSummaryResponse summary = report.getFinancialSummary();
        csv.append("총 매출,").append(summary.getTotalRevenue()).append('\n');
        csv.append("총 비용,").append(summary.getTotalExpense()).append('\n');
        csv.append("영업이익,").append(summary.getOperatingIncome()).append('\n');
        csv.append("당기순이익,").append(summary.getNetIncome()).append('\n');
        csv.append("총 자산,").append(summary.getTotalAssets()).append('\n');
        csv.append("총 부채,").append(summary.getTotalLiabilities()).append("\n\n");
        csv.append("계정코드,계정명,유형,기초잔액,차변,대변,당기증감,기말잔액\n");
        for (AccountAnalysisRow row : report.getAccountAnalysis()) {
            csv.append(escape(row.getAccountCode())).append(',')
                    .append(escape(row.getAccountName())).append(',')
                    .append(row.getAccountType()).append(',')
                    .append(row.getOpeningBalance()).append(',')
                    .append(row.getPeriodDebit()).append(',')
                    .append(row.getPeriodCredit()).append(',')
                    .append(row.getPeriodChange()).append(',')
                    .append(row.getClosingBalance()).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public byte[] exportPdf(LocalDate startDate, LocalDate endDate, AccountingReportBasis reportBasis, AccountingReportType reportType) {
        AccountingReportResponse report = getReport(startDate, endDate, reportBasis, reportType);
        String text = "DDUK ERP Accounting Report\n"
                + report.getStartDate() + " ~ " + report.getEndDate() + "\n"
                + "Revenue: " + report.getFinancialSummary().getTotalRevenue() + "\n"
                + "Expense: " + report.getFinancialSummary().getTotalExpense() + "\n"
                + "Net Income: " + report.getFinancialSummary().getNetIncome() + "\n";
        return minimalPdf(text);
    }

    private FinancialSummaryResponse financialSummary(ReportRange range, List<AccountAnalysisRow> accountRows) {
        Map<AccountType, BigDecimal> totals = accountRows.stream()
                .filter(AccountAnalysisRow::isLeaf)
                .collect(Collectors.groupingBy(AccountAnalysisRow::getAccountType,
                        () -> new EnumMap<>(AccountType.class),
                        Collectors.reducing(BigDecimal.ZERO, AccountAnalysisRow::getClosingBalance, BigDecimal::add)));
        Map<AccountType, BigDecimal> previousTotals = previousTotals(range);

        BigDecimal revenue = totals.getOrDefault(AccountType.REVENUE, BigDecimal.ZERO);
        BigDecimal expense = totals.getOrDefault(AccountType.EXPENSE, BigDecimal.ZERO);
        BigDecimal assets = totals.getOrDefault(AccountType.ASSET, BigDecimal.ZERO);
        BigDecimal liabilities = totals.getOrDefault(AccountType.LIABILITY, BigDecimal.ZERO);
        BigDecimal equity = totals.getOrDefault(AccountType.EQUITY, BigDecimal.ZERO);
        BigDecimal operatingIncome = revenue.subtract(expense);
        BigDecimal netIncome = operatingIncome;
        BigDecimal debtRatio = ratio(liabilities, equity);
        BigDecimal currentRatio = ratio(assets, liabilities);

        BalanceSheetSummary balanceSheet = BalanceSheetSummary.builder()
                .totalAssets(assets)
                .totalLiabilities(liabilities)
                .totalEquity(equity)
                .debtRatio(debtRatio)
                .currentRatio(currentRatio)
                .balanced(assets.compareTo(liabilities.add(equity)) == 0)
                .build();
        ProfitLossResponse profitLoss = ProfitLossResponse.builder()
                .revenue(revenue)
                .expense(expense)
                .operatingIncome(operatingIncome)
                .netIncome(netIncome)
                .build();

        return FinancialSummaryResponse.builder()
                .totalRevenue(revenue)
                .revenueChangeRate(changeRate(revenue, previousTotals.getOrDefault(AccountType.REVENUE, BigDecimal.ZERO)))
                .totalExpense(expense)
                .expenseChangeRate(changeRate(expense, previousTotals.getOrDefault(AccountType.EXPENSE, BigDecimal.ZERO)))
                .operatingIncome(operatingIncome)
                .netIncome(netIncome)
                .totalAssets(assets)
                .totalLiabilities(liabilities)
                .totalEquity(equity)
                .debtRatio(debtRatio)
                .currentRatio(currentRatio)
                .balanceSheetSummary(balanceSheet)
                .profitLossSummary(profitLoss)
                .build();
    }

    private Map<AccountType, BigDecimal> previousTotals(ReportRange range) {
        long days = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(range.startDate(), range.endDate()) + 1);
        LocalDate previousEnd = range.startDate().minusDays(1);
        LocalDate previousStart = previousEnd.minusDays(days - 1);
        Map<AccountType, BigDecimal> result = new EnumMap<>(AccountType.class);
        for (Object[] row : journalEntryRepository.aggregatePostedByAccountTypeBetweenDates(previousStart, previousEnd)) {
            AccountType type = (AccountType) row[0];
            result.put(type, signedAmount(type, decimalAt(row, 1), decimalAt(row, 2)));
        }
        return result;
    }

    private TrialBalanceResponse trialBalance(ReportRange range) {
        TrialBalanceSearchCondition condition = new TrialBalanceSearchCondition();
        condition.setStartDate(range.startDate());
        condition.setEndDate(range.endDate());
        condition.setReportBasis(ReportBasis.MONTHLY);
        condition.setAccountLevel(AccountLevelFilter.ALL);
        condition.setIncludeZeroBalance(false);
        condition.setIncludeSubAccounts(true);
        return trialBalanceReportService.getTrialBalance(condition);
    }

    private List<AccountAnalysisRow> accountAnalysisRows(TrialBalanceResponse trialBalance) {
        return trialBalance.getRows().stream()
                .map(row -> {
                    BigDecimal opening = signed(row.getNormalBalance(), row.getOpeningDebit(), row.getOpeningCredit());
                    BigDecimal periodChange = signed(row.getNormalBalance(), row.getPeriodDebit(), row.getPeriodCredit());
                    BigDecimal closing = signed(row.getNormalBalance(), row.getClosingDebit(), row.getClosingCredit());
                    return AccountAnalysisRow.builder()
                            .accountId(row.getAccountId())
                            .parentAccountId(row.getParentAccountId())
                            .accountCode(row.getAccountCode())
                            .accountName(row.getAccountName())
                            .accountType(row.getAccountType())
                            .normalBalance(row.getNormalBalance())
                            .level(row.getLevel())
                            .leaf(row.isLeaf())
                            .openingBalance(opening)
                            .periodDebit(row.getPeriodDebit())
                            .periodCredit(row.getPeriodCredit())
                            .periodChange(periodChange)
                            .closingBalance(closing)
                            .build();
                })
                .toList();
    }

    private List<SalesAnalysisRow> getSalesAnalysis(LocalDate startDate, LocalDate endDate) {
        Map<String, BigDecimal> previous = voucherRepository.aggregateVendorAmounts(
                        startDate.minusDays(java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1),
                        startDate.minusDays(1),
                        VoucherType.SALES,
                        REPORT_VOUCHER_STATUSES)
                .stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> decimalAt(row, 3), (a, b) -> a, LinkedHashMap::new));
        return voucherRepository.aggregateVendorAmounts(startDate, endDate, VoucherType.SALES, REPORT_VOUCHER_STATUSES).stream()
                .map(row -> {
                    String vendorName = (String) row[0];
                    BigDecimal supply = decimalAt(row, 1);
                    BigDecimal vat = decimalAt(row, 2);
                    BigDecimal total = decimalAt(row, 3);
                    return SalesAnalysisRow.builder()
                            .vendorName(vendorName)
                            .salesAmount(total)
                            .vatAmount(vat)
                            .netSales(supply)
                            .changeRate(changeRate(total, previous.getOrDefault(vendorName, BigDecimal.ZERO)))
                            .build();
                })
                .limit(20)
                .toList();
    }

    private List<ExpenseAnalysisRow> getExpenseAnalysis(List<AccountAnalysisRow> accountRows) {
        BigDecimal totalExpense = accountRows.stream()
                .filter(row -> row.isLeaf() && row.getAccountType() == AccountType.EXPENSE)
                .map(AccountAnalysisRow::getClosingBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return accountRows.stream()
                .filter(row -> row.isLeaf() && row.getAccountType() == AccountType.EXPENSE)
                .sorted(Comparator.comparing(AccountAnalysisRow::getClosingBalance).reversed())
                .limit(20)
                .map(row -> ExpenseAnalysisRow.builder()
                        .accountCode(row.getAccountCode())
                        .accountName(row.getAccountName())
                        .amount(row.getClosingBalance())
                        .ratio(ratio(row.getClosingBalance(), totalExpense))
                        .build())
                .toList();
    }

    private List<VoucherFlowRow> getVoucherFlows(LocalDate startDate, LocalDate endDate) {
        Map<String, Map<VoucherStatus, Long>> flows = new LinkedHashMap<>();
        YearMonth cursor = YearMonth.from(startDate);
        YearMonth endMonth = YearMonth.from(endDate);
        while (!cursor.isAfter(endMonth)) {
            flows.put(periodKey(cursor), new EnumMap<>(VoucherStatus.class));
            cursor = cursor.plusMonths(1);
        }
        for (Object[] row : voucherRepository.countMonthlyStatusByVoucherDateBetween(startDate, endDate)) {
            String key = String.format("%d-%02d", ((Number) row[0]).intValue(), ((Number) row[1]).intValue());
            flows.computeIfAbsent(key, ignored -> new EnumMap<>(VoucherStatus.class))
                    .put((VoucherStatus) row[2], ((Number) row[3]).longValue());
        }
        return flows.entrySet().stream()
                .map(entry -> {
                    Map<VoucherStatus, Long> counts = entry.getValue();
                    long total = counts.values().stream().mapToLong(Long::longValue).sum();
                    return VoucherFlowRow.builder()
                            .period(entry.getKey())
                            .totalCount(total)
                            .approvedCount(counts.getOrDefault(VoucherStatus.APPROVED, 0L))
                            .postedCount(counts.getOrDefault(VoucherStatus.POSTED, 0L))
                            .cancelledCount(counts.getOrDefault(VoucherStatus.CANCELLED, 0L))
                            .build();
                })
                .toList();
    }

    private PayrollAnalysisResponse getPayrollAnalysis(LocalDate startDate, LocalDate endDate) {
        String startYearMonth = periodKey(YearMonth.from(startDate));
        String endYearMonth = periodKey(YearMonth.from(endDate));
        Object[] totals = payrollLedgerRepository.sumPayrollAmountsBetweenYearMonth(startYearMonth, endYearMonth, REPORT_PAYROLL_STATUSES);
        BigDecimal bonusAmount = payrollLedgerRepository.sumBonusAmountBetweenYearMonth(startYearMonth, endYearMonth, REPORT_PAYROLL_STATUSES);
        List<PayrollDepartmentRow> departments = payrollLedgerRepository.aggregateDepartmentPayroll(startYearMonth, endYearMonth, REPORT_PAYROLL_STATUSES).stream()
                .map(row -> PayrollDepartmentRow.builder()
                        .departmentName((String) row[0])
                        .grossAmount(decimalAt(row, 1))
                        .deductionAmount(decimalAt(row, 2))
                        .netAmount(decimalAt(row, 3))
                        .headCount(((Number) row[4]).longValue())
                        .build())
                .toList();
        return PayrollAnalysisResponse.builder()
                .grossAmount(decimalAt(totals, 0))
                .bonusAmount(bonusAmount)
                .deductionAmount(decimalAt(totals, 1))
                .netAmount(decimalAt(totals, 2))
                .departments(departments)
                .build();
    }

    private List<BalanceCompositionResponse> balanceComposition(FinancialSummaryResponse summary) {
        BigDecimal asset = summary.getTotalAssets().abs();
        BigDecimal liability = summary.getTotalLiabilities().abs();
        BigDecimal equity = summary.getTotalEquity().abs();
        BigDecimal total = asset.add(liability).add(equity);
        return List.of(
                new BalanceCompositionResponse(AccountType.ASSET, "자산", asset, ratio(asset, total)),
                new BalanceCompositionResponse(AccountType.LIABILITY, "부채", liability, ratio(liability, total)),
                new BalanceCompositionResponse(AccountType.EQUITY, "자본", equity, ratio(equity, total))
        );
    }

    private ReportRange normalize(LocalDate startDate, LocalDate endDate, AccountingReportBasis reportBasis, AccountingReportType reportType) {
        YearMonth currentMonth = YearMonth.now();
        LocalDate normalizedStart = startDate == null ? currentMonth.atDay(1) : startDate;
        LocalDate normalizedEnd = endDate == null ? currentMonth.atEndOfMonth() : endDate;
        if (normalizedEnd.isBefore(normalizedStart)) {
            throw new IllegalArgumentException("endDate must be greater than or equal to startDate.");
        }
        return new ReportRange(normalizedStart, normalizedEnd,
                reportBasis == null ? AccountingReportBasis.MONTHLY : reportBasis,
                reportType == null ? AccountingReportType.COMPREHENSIVE : reportType);
    }

    private BigDecimal signedAmount(AccountType type, BigDecimal debit, BigDecimal credit) {
        AccountSide normalSide = (type == AccountType.ASSET || type == AccountType.EXPENSE) ? AccountSide.DEBIT : AccountSide.CREDIT;
        return signed(normalSide, debit, credit);
    }

    private BigDecimal signed(AccountSide normalSide, BigDecimal debit, BigDecimal credit) {
        return normalSide == AccountSide.DEBIT ? debit.subtract(credit) : credit.subtract(debit);
    }

    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return numerator.multiply(BigDecimal.valueOf(100)).divide(denominator.abs(), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal changeRate(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return current.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous.abs(), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal decimalAt(Object[] row, int index) {
        if (row == null || row.length <= index || row[index] == null) {
            return BigDecimal.ZERO;
        }
        if (row[index] instanceof BigDecimal value) {
            return value;
        }
        return new BigDecimal(row[index].toString());
    }

    private String periodKey(YearMonth yearMonth) {
        return yearMonth.getYear() + "-" + String.format("%02d", yearMonth.getMonthValue());
    }

    private String escape(String value) {
        if (value == null) return "";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private byte[] minimalPdf(String text) {
        String safe = text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)").replace("\n", "\\n");
        String stream = "BT /F1 12 Tf 50 760 Td (" + safe + ") Tj ET";
        String pdf = "%PDF-1.4\n"
                + "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n"
                + "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n"
                + "3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >> endobj\n"
                + "4 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n"
                + "5 0 obj << /Length " + stream.length() + " >> stream\n" + stream + "\nendstream endobj\n"
                + "xref\n0 6\n0000000000 65535 f \n"
                + "trailer << /Root 1 0 R /Size 6 >>\nstartxref\n0\n%%EOF";
        return pdf.getBytes(StandardCharsets.UTF_8);
    }

    private record ReportRange(LocalDate startDate, LocalDate endDate, AccountingReportBasis reportBasis, AccountingReportType reportType) {
    }
}
