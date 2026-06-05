package com.dduk.service.accounting;

import com.dduk.entity.accounting.Account;
import com.dduk.repository.accounting.AccountRepository;
import com.dduk.repository.accounting.JournalEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 재무 보고서 서비스
 * - DB aggregation(SUM/GROUP BY) 기반으로 계산
 * - 메모리 전체 조회 방식 금지
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;

    /**
     * 합계잔액시산표
     * - 계정코드별 차변합계 / 대변합계 / 잔액
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTrialBalance(Integer fiscalYear, Integer fiscalMonth) {
        Map<String, Account> accountMap = accountRepository.findAllLeafAccounts().stream()
                .collect(Collectors.toMap(Account::getCode, a -> a));

        List<Object[]> rows = journalEntryRepository.aggregateByAccountCode(fiscalYear, fiscalMonth);

        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        for (Object[] row : rows) {
            String code = (String) row[0];
            BigDecimal debit  = toDecimal(row[1]);
            BigDecimal credit = toDecimal(row[2]);
            Account account = accountMap.get(code);
            if (account == null) continue;

            BigDecimal balance = account.isDebitNormal()
                    ? debit.subtract(credit)
                    : credit.subtract(debit);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code",        code);
            item.put("name",        account.getName());
            item.put("type",        account.getType().name());
            item.put("normalBalance", account.getNormalBalance().name());
            item.put("totalDebit",  debit);
            item.put("totalCredit", credit);
            item.put("balance",     balance);
            items.add(item);

            totalDebit  = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fiscalYear",   fiscalYear);
        result.put("fiscalMonth",  fiscalMonth);
        result.put("items",        items);
        result.put("totalDebit",   totalDebit);
        result.put("totalCredit",  totalCredit);
        result.put("isBalanced",   totalDebit.compareTo(totalCredit) == 0);
        return result;
    }

    /**
     * 총계정원장 (특정 계정)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getGeneralLedger(String accountCode, Integer fiscalYear, Integer fiscalMonth) {
        Account account = accountRepository.findByCode(accountCode)
                .orElseThrow(() -> new IllegalArgumentException("계정코드를 찾을 수 없습니다: " + accountCode));

        List<Object[]> rows = journalEntryRepository.findGeneralLedgerByAccount(accountCode, fiscalYear, fiscalMonth);

        List<Map<String, Object>> entries = new ArrayList<>();
        BigDecimal runningBalance = BigDecimal.ZERO;

        for (Object[] row : rows) {
            BigDecimal debit  = toDecimal(row[3]);
            BigDecimal credit = toDecimal(row[4]);
            if (account.isDebitNormal()) {
                runningBalance = runningBalance.add(debit).subtract(credit);
            } else {
                runningBalance = runningBalance.add(credit).subtract(debit);
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("date",        row[0]);
            entry.put("journalNo",   row[1]);
            entry.put("description", row[2]);
            entry.put("debit",       debit);
            entry.put("credit",      credit);
            entry.put("lineDesc",    row[5]);
            entry.put("balance",     runningBalance);
            entries.add(entry);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accountCode",  account.getCode());
        result.put("accountName",  account.getName());
        result.put("accountType",  account.getType().name());
        result.put("fiscalYear",   fiscalYear);
        result.put("fiscalMonth",  fiscalMonth);
        result.put("entries",      entries);
        result.put("closingBalance", runningBalance);
        return result;
    }

    /**
     * 손익계산서 (REVENUE - EXPENSE)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getProfitAndLoss(Integer fiscalYear, Integer fiscalMonth) {
        Map<String, BigDecimal> balances = buildBalanceMap(fiscalYear, fiscalMonth);
        List<Account> accounts = accountRepository.findAllLeafAccounts();

        List<Map<String, Object>> revenue  = buildSection(accounts, balances, "REVENUE");
        List<Map<String, Object>> expenses = buildSection(accounts, balances, "EXPENSE");

        BigDecimal totalRevenue  = sumSection(revenue);
        BigDecimal totalExpenses = sumSection(expenses);
        BigDecimal netIncome     = totalRevenue.subtract(totalExpenses);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fiscalYear",    fiscalYear);
        result.put("fiscalMonth",   fiscalMonth);
        result.put("revenue",       revenue);
        result.put("expenses",      expenses);
        result.put("totalRevenue",  totalRevenue);
        result.put("totalExpenses", totalExpenses);
        result.put("netIncome",     netIncome);
        return result;
    }

    /**
     * 재무상태표 (ASSET = LIABILITY + EQUITY)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getBalanceSheet(Integer fiscalYear, Integer fiscalMonth) {
        Map<String, BigDecimal> balances = buildBalanceMap(fiscalYear, fiscalMonth);
        List<Account> accounts = accountRepository.findAllLeafAccounts();

        List<Map<String, Object>> assets      = buildSection(accounts, balances, "ASSET");
        List<Map<String, Object>> liabilities = buildSection(accounts, balances, "LIABILITY");
        List<Map<String, Object>> equity      = buildSection(accounts, balances, "EQUITY");

        BigDecimal totalAssets      = sumSection(assets);
        BigDecimal totalLiabilities = sumSection(liabilities);
        BigDecimal totalEquity      = sumSection(equity);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fiscalYear",        fiscalYear);
        result.put("fiscalMonth",       fiscalMonth);
        result.put("assets",            assets);
        result.put("liabilities",       liabilities);
        result.put("equity",            equity);
        result.put("totalAssets",       totalAssets);
        result.put("totalLiabilities",  totalLiabilities);
        result.put("totalEquity",       totalEquity);
        result.put("isBalanced",        totalAssets.compareTo(totalLiabilities.add(totalEquity)) == 0);
        return result;
    }

    // ── 내부 헬퍼 ────────────────────────────────────────────────────────

    private Map<String, BigDecimal> buildBalanceMap(Integer fiscalYear, Integer fiscalMonth) {
        List<Object[]> rows = journalEntryRepository.aggregateByAccountCode(fiscalYear, fiscalMonth);
        Map<String, Account> accountMap = accountRepository.findAllLeafAccounts().stream()
                .collect(Collectors.toMap(Account::getCode, a -> a));

        Map<String, BigDecimal> balances = new HashMap<>();
        for (Object[] row : rows) {
            String code   = (String) row[0];
            BigDecimal dr = toDecimal(row[1]);
            BigDecimal cr = toDecimal(row[2]);
            Account account = accountMap.get(code);
            if (account == null) continue;
            BigDecimal balance = account.isDebitNormal()
                    ? dr.subtract(cr)
                    : cr.subtract(dr);
            balances.put(code, balance);
        }
        return balances;
    }

    private List<Map<String, Object>> buildSection(List<Account> accounts,
                                                    Map<String, BigDecimal> balances,
                                                    String type) {
        return accounts.stream()
                .filter(a -> type.equals(a.getType().name()))
                .map(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("code",    a.getCode());
                    m.put("name",    a.getName());
                    m.put("balance", balances.getOrDefault(a.getCode(), BigDecimal.ZERO));
                    return m;
                })
                .collect(Collectors.toList());
    }

    private BigDecimal sumSection(List<Map<String, Object>> items) {
        return items.stream()
                .map(i -> (BigDecimal) i.get("balance"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal toDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal) return (BigDecimal) val;
        return new BigDecimal(val.toString());
    }

    // 하위 호환용 (기존 API 호환)
    @Transactional(readOnly = true)
    public Map<String, Object> generateBalanceSheet() {
        return getBalanceSheet(null, null);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> generateProfitAndLoss() {
        return getProfitAndLoss(null, null);
    }
}
