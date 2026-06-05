package com.dduk.dto.accounting.report;

import java.math.BigDecimal;

public record TrialBalanceAmount(BigDecimal debit, BigDecimal credit) {
    public static TrialBalanceAmount zero() {
        return new TrialBalanceAmount(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public TrialBalanceAmount add(TrialBalanceAmount other) {
        return new TrialBalanceAmount(debit.add(other.debit()), credit.add(other.credit()));
    }
}
