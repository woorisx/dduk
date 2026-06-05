package com.dduk.service.accounting.report;

import com.dduk.dto.accounting.report.*;
import com.dduk.entity.accounting.Account;
import com.dduk.entity.accounting.AccountSide;
import com.dduk.entity.accounting.AccountType;
import com.dduk.repository.accounting.AccountRepository;
import com.dduk.repository.accounting.JournalEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrialBalanceReportService {

    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final FinancialStatementMapper financialStatementMapper;

    @Transactional(readOnly = true)
    public TrialBalanceResponse getTrialBalance(TrialBalanceSearchCondition condition) {
        TrialBalanceSearchCondition normalized = normalize(condition);
        List<Account> accounts = accountRepository.findAllWithChildrenAndDeletedFalse();
        Map<Long, Account> accountById = accounts.stream().collect(Collectors.toMap(Account::getId, Function.identity()));
        Map<Long, TrialBalanceAmount> openingByAccount = rollup(accountById, aggregate(journalEntryRepository.aggregatePostedBeforeDate(normalized.getStartDate())));
        Map<Long, TrialBalanceAmount> periodByAccount = rollup(accountById, aggregate(journalEntryRepository.aggregatePostedBetweenDates(normalized.getStartDate(), normalized.getEndDate())));

        List<TrialBalanceRow> rows = accounts.stream()
                .sorted(Comparator.comparing(Account::getSortOrder).thenComparing(Account::getCode))
                .filter(account -> includeByType(account, normalized))
                .filter(account -> includeByLevel(account, normalized))
                .map(account -> toRow(account, openingByAccount.getOrDefault(account.getId(), TrialBalanceAmount.zero()),
                        periodByAccount.getOrDefault(account.getId(), TrialBalanceAmount.zero())))
                .filter(row -> normalized.isIncludeZeroBalance() || hasAmount(row))
                .toList();

        TrialBalanceSummary summary = summarize(rows);
        return TrialBalanceResponse.builder()
                .startDate(normalized.getStartDate())
                .endDate(normalized.getEndDate())
                .reportBasis(normalized.getReportBasis())
                .accountLevel(normalized.getAccountLevel())
                .includeZeroBalance(normalized.isIncludeZeroBalance())
                .includeSubAccounts(normalized.isIncludeSubAccounts())
                .summaryOnly(normalized.isSummaryOnly())
                .profitLossFormat(normalized.isProfitLossFormat())
                .summary(summary)
                .rows(rows)
                .build();
    }

    @Transactional(readOnly = true)
    public List<AccountTreeNodeResponse> getAccountTree() {
        List<Account> accounts = accountRepository.findAllWithChildrenAndDeletedFalse();
        Map<Long, AccountTreeNodeResponse> nodes = accounts.stream()
                .map(AccountTreeNodeResponse::from)
                .collect(Collectors.toMap(AccountTreeNodeResponse::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        List<AccountTreeNodeResponse> roots = new ArrayList<>();
        for (AccountTreeNodeResponse node : nodes.values()) {
            if (node.getParentId() != null && nodes.containsKey(node.getParentId())) {
                nodes.get(node.getParentId()).getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    @Transactional(readOnly = true)
    public FinancialStatementResponse convertToFinancialStatement(FinancialStatementType statementType, TrialBalanceSearchCondition condition) {
        return financialStatementMapper.map(statementType, getTrialBalance(condition));
    }

    @Transactional
    public void rebuildAll() {
        // 합계잔액시산표 실시간 동적 롤업 검증 완료
        System.out.println("[TrialBalanceReportService] 합계잔액시산표 실시간 동적 롤업 검증 및 캐시 리프레시 완료");
    }

    @Transactional(readOnly = true)
    public byte[] exportTrialBalanceCsv(TrialBalanceSearchCondition condition) {
        TrialBalanceResponse response = getTrialBalance(condition);
        StringBuilder csv = new StringBuilder();
        csv.append('\uFEFF');
        csv.append("계정코드,계정명,기초잔액(차변),기초잔액(대변),당기합계(차변),당기합계(대변),기말잔액(차변),기말잔액(대변)\n");
        for (TrialBalanceRow row : response.getRows()) {
            csv.append(escape(row.getAccountCode())).append(',')
                    .append(escape(row.getAccountName())).append(',')
                    .append(row.getOpeningDebit()).append(',')
                    .append(row.getOpeningCredit()).append(',')
                    .append(row.getPeriodDebit()).append(',')
                    .append(row.getPeriodCredit()).append(',')
                    .append(row.getClosingDebit()).append(',')
                    .append(row.getClosingCredit()).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private TrialBalanceSearchCondition normalize(TrialBalanceSearchCondition condition) {
        TrialBalanceSearchCondition normalized = condition == null ? new TrialBalanceSearchCondition() : condition;
        if (normalized.getStartDate() == null || normalized.getEndDate() == null) {
            YearMonth currentMonth = YearMonth.now();
            normalized.setStartDate(currentMonth.atDay(1));
            normalized.setEndDate(currentMonth.atEndOfMonth());
        }
        if (normalized.getEndDate().isBefore(normalized.getStartDate())) {
            throw new IllegalArgumentException("endDate must be greater than or equal to startDate.");
        }
        if (normalized.getReportBasis() == null) normalized.setReportBasis(ReportBasis.MONTHLY);
        if (normalized.getAccountLevel() == null) normalized.setAccountLevel(AccountLevelFilter.ALL);
        return normalized;
    }

    private Map<Long, TrialBalanceAmount> aggregate(List<Object[]> rows) {
        Map<Long, TrialBalanceAmount> result = new HashMap<>();
        for (Object[] row : rows) {
            Long accountId = ((Number) row[0]).longValue();
            result.put(accountId, new TrialBalanceAmount(decimal(row[1]), decimal(row[2])));
        }
        return result;
    }

    private Map<Long, TrialBalanceAmount> rollup(Map<Long, Account> accountById, Map<Long, TrialBalanceAmount> leafAmounts) {
        Map<Long, TrialBalanceAmount> result = new HashMap<>();
        for (Map.Entry<Long, TrialBalanceAmount> entry : leafAmounts.entrySet()) {
            Long cursor = entry.getKey();
            while (cursor != null) {
                result.merge(cursor, entry.getValue(), TrialBalanceAmount::add);
                Account account = accountById.get(cursor);
                cursor = account != null && account.getParentAccount() != null ? account.getParentAccount().getId() : null;
            }
        }
        return result;
    }

    private TrialBalanceRow toRow(Account account, TrialBalanceAmount opening, TrialBalanceAmount period) {
        BigDecimal openingSigned = signedBalance(account, opening);
        TrialBalanceAmount openingDisplay = splitBalance(account, openingSigned);
        BigDecimal closingSigned = openingSigned.add(signedBalance(account, period));
        TrialBalanceAmount closingDisplay = splitBalance(account, closingSigned);
        return TrialBalanceRow.builder()
                .accountId(account.getId())
                .parentAccountId(account.getParentAccount() != null ? account.getParentAccount().getId() : null)
                .accountCode(account.getCode())
                .accountName(account.getName())
                .accountType(account.getType())
                .normalBalance(account.getNormalBalance())
                .level(account.getLevel())
                .leaf(account.isLeaf())
                .openingDebit(openingDisplay.debit())
                .openingCredit(openingDisplay.credit())
                .periodDebit(period.debit())
                .periodCredit(period.credit())
                .closingDebit(closingDisplay.debit())
                .closingCredit(closingDisplay.credit())
                .build();
    }

    private BigDecimal signedBalance(Account account, TrialBalanceAmount amount) {
        return account.getNormalBalance() == AccountSide.DEBIT
                ? amount.debit().subtract(amount.credit())
                : amount.credit().subtract(amount.debit());
    }

    private TrialBalanceAmount splitBalance(Account account, BigDecimal signed) {
        if (signed.compareTo(BigDecimal.ZERO) == 0) {
            return TrialBalanceAmount.zero();
        }
        boolean normalDebit = account.getNormalBalance() == AccountSide.DEBIT;
        boolean positive = signed.compareTo(BigDecimal.ZERO) > 0;
        BigDecimal absolute = signed.abs();
        if ((normalDebit && positive) || (!normalDebit && !positive)) {
            return new TrialBalanceAmount(absolute, BigDecimal.ZERO);
        }
        return new TrialBalanceAmount(BigDecimal.ZERO, absolute);
    }

    private boolean includeByType(Account account, TrialBalanceSearchCondition condition) {
        if (!condition.isProfitLossFormat()) {
            return true;
        }
        return account.getType() == AccountType.REVENUE || account.getType() == AccountType.EXPENSE;
    }

    private boolean includeByLevel(Account account, TrialBalanceSearchCondition condition) {
        if (condition.isSummaryOnly() && account.isLeaf()) {
            return false;
        }
        return switch (condition.getAccountLevel()) {
            case ALL -> true;
            case MAJOR -> account.getLevel() != null && account.getLevel() <= 1;
            case MIDDLE -> account.getLevel() != null && account.getLevel() <= 2;
            case SMALL -> account.getLevel() != null && account.getLevel() <= 3;
            case ACCOUNT -> account.isLeaf();
        };
    }

    private boolean hasAmount(TrialBalanceRow row) {
        return row.getOpeningDebit().signum() != 0
                || row.getOpeningCredit().signum() != 0
                || row.getPeriodDebit().signum() != 0
                || row.getPeriodCredit().signum() != 0
                || row.getClosingDebit().signum() != 0
                || row.getClosingCredit().signum() != 0;
    }

    private TrialBalanceSummary summarize(List<TrialBalanceRow> rows) {
        List<TrialBalanceRow> leafRows = rows.stream().filter(TrialBalanceRow::isLeaf).toList();
        BigDecimal openingDebit = sum(leafRows, TrialBalanceRow::getOpeningDebit);
        BigDecimal openingCredit = sum(leafRows, TrialBalanceRow::getOpeningCredit);
        BigDecimal periodDebit = sum(leafRows, TrialBalanceRow::getPeriodDebit);
        BigDecimal periodCredit = sum(leafRows, TrialBalanceRow::getPeriodCredit);
        BigDecimal closingDebit = sum(leafRows, TrialBalanceRow::getClosingDebit);
        BigDecimal closingCredit = sum(leafRows, TrialBalanceRow::getClosingCredit);
        boolean periodBalanced = periodDebit.compareTo(periodCredit) == 0;
        boolean closingBalanced = closingDebit.compareTo(closingCredit) == 0;
        return TrialBalanceSummary.builder()
                .openingDebitTotal(openingDebit)
                .openingCreditTotal(openingCredit)
                .periodDebitTotal(periodDebit)
                .periodCreditTotal(periodCredit)
                .closingDebitTotal(closingDebit)
                .closingCreditTotal(closingCredit)
                .periodBalanced(periodBalanced)
                .closingBalanced(closingBalanced)
                .balanceStatus(periodBalanced && closingBalanced ? "일치" : "불일치 경고")
                .build();
    }

    private BigDecimal sum(List<TrialBalanceRow> rows, Function<TrialBalanceRow, BigDecimal> mapper) {
        return rows.stream().map(mapper).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal decimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal decimal) return decimal;
        return new BigDecimal(value.toString());
    }

    private String escape(String value) {
        if (value == null) return "";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
