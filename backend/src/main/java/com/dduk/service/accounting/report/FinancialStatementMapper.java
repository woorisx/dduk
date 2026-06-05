package com.dduk.service.accounting.report;

import com.dduk.dto.accounting.report.*;
import com.dduk.entity.accounting.AccountType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Component
public class FinancialStatementMapper {

    public FinancialStatementResponse map(FinancialStatementType statementType, TrialBalanceResponse trialBalance) {
        List<TrialBalanceRow> rows = trialBalance.getRows();
        List<FinancialStatementSection> sections = switch (statementType) {
            case BALANCE_SHEET -> List.of(
                    section("ASSET", "자산", rows, AccountType.ASSET),
                    section("LIABILITY", "부채", rows, AccountType.LIABILITY),
                    section("EQUITY", "자본", rows, AccountType.EQUITY)
            );
            case PROFIT_LOSS -> buildProfitLossSections(rows);
            case MANUFACTURING_COST, CASH_FLOW, VAT_REPORT -> List.of();
        };
        return FinancialStatementResponse.builder()
                .statementType(statementType)
                .startDate(trialBalance.getStartDate())
                .endDate(trialBalance.getEndDate())
                .sections(sections)
                .build();
    }

    private List<FinancialStatementSection> buildProfitLossSections(List<TrialBalanceRow> rows) {
        FinancialStatementSection revenue = section("REVENUE", "매출/수익", rows, AccountType.REVENUE);
        FinancialStatementSection expense = section("EXPENSE", "비용", rows, AccountType.EXPENSE);
        FinancialStatementSection netIncome = FinancialStatementSection.builder()
                .sectionCode("NET_INCOME")
                .sectionName("당기순이익")
                .amount(revenue.getAmount().subtract(expense.getAmount()))
                .rows(List.of())
                .build();
        return List.of(revenue, expense, netIncome);
    }

    private FinancialStatementSection section(String code, String name, List<TrialBalanceRow> rows, AccountType type) {
        List<TrialBalanceRow> sectionRows = rows.stream()
                .filter(row -> row.getAccountType() == type)
                .toList();
        BigDecimal amount = sectionRows.stream()
                .filter(TrialBalanceRow::isLeaf)
                .map(this::closingBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return FinancialStatementSection.builder()
                .sectionCode(code)
                .sectionName(name)
                .amount(amount)
                .rows(sectionRows)
                .build();
    }

    private BigDecimal closingBalance(TrialBalanceRow row) {
        return row.getClosingDebit().compareTo(BigDecimal.ZERO) > 0 ? row.getClosingDebit() : row.getClosingCredit();
    }
}
