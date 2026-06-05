package com.dduk.service.accounting.autojounal;

import com.dduk.dto.accounting.JournalLineRequest;
import com.dduk.entity.inventory.PurchaseOrder;
import com.dduk.service.accounting.AccountingConstants;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 발주(매입) 자동분개 전략
 * [차] 재고자산 / [대] 외상매입금
 */
@Component
public class PurchaseJournalStrategy implements JournalStrategy {

    @Override
    public String getSupportedSourceType() {
        return AccountingConstants.SOURCE_PURCHASE;
    }

    @Override
    public LocalDate getJournalDate(Object source) {
        return ((PurchaseOrder) source).getOrderDate();
    }

    @Override
    public String buildDescription(Object source) {
        PurchaseOrder order = (PurchaseOrder) source;
        return "매입확정 " + order.getPurchaseOrderNo()
                + " (" + order.getVendor().getName() + ")";
    }

    @Override
    public List<JournalLineRequest> buildLines(Object source) {
        PurchaseOrder order = (PurchaseOrder) source;
        BigDecimal amount = order.getTotalAmount();

        JournalLineRequest debit = new JournalLineRequest();
        debit.setAccountCode(AccountingConstants.INVENTORY_ASSET);
        debit.setDebitAmount(amount);
        debit.setCreditAmount(BigDecimal.ZERO);
        debit.setDescription("재고자산 증가");

        JournalLineRequest credit = new JournalLineRequest();
        credit.setAccountCode(AccountingConstants.ACCOUNTS_PAYABLE);
        credit.setDebitAmount(BigDecimal.ZERO);
        credit.setCreditAmount(amount);
        credit.setDescription("외상매입금 증가");

        return List.of(debit, credit);
    }
}
