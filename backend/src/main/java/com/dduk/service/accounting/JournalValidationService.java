package com.dduk.service.accounting;

import com.dduk.dto.accounting.JournalLineRequest;
import com.dduk.entity.accounting.Account;
import com.dduk.entity.accounting.JournalEntry;
import com.dduk.repository.accounting.AccountRepository;
import com.dduk.entity.accounting.period.AccountingPeriodStatus;
import com.dduk.repository.accounting.period.AccountingPeriodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JournalValidationService {

    private final AccountingPeriodRepository accountingPeriodRepository;
    private final AccountRepository accountRepository;

    public void validateLines(List<JournalLineRequest> lineRequests) {
        if (lineRequests == null || lineRequests.isEmpty()) {
            throw new IllegalArgumentException("전표 라인이 비어 있습니다.");
        }

        if (lineRequests.size() < 2) {
            throw new IllegalArgumentException("전표 라인은 최소 2개 이상이어야 합니다.");
        }

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        for (JournalLineRequest req : lineRequests) {
            Account account = accountRepository.findByCode(req.getAccountCode())
                    .orElseThrow(() -> new IllegalArgumentException("계정코드를 찾을 수 없습니다: " + req.getAccountCode()));

            if (!Boolean.TRUE.equals(account.getIsActive())) {
                throw new IllegalArgumentException(
                        "비활성화된 계정입니다: " + req.getAccountCode() + " (" + account.getName() + ")");
            }

            if (!Boolean.TRUE.equals(account.getAllowPosting())) {
                throw new IllegalArgumentException(
                        "기표(posting)가 차단된 계정입니다. 계정코드: " + req.getAccountCode() + " (" + account.getName() + ")");
            }

            if (!account.isLeaf()) {
                throw new IllegalArgumentException(
                        "말단(Leaf) 계정만 전표 기표가 가능합니다. 상위 계정코드: " + req.getAccountCode() + " (" + account.getName() + ")");
            }

            if (req.getDebitAmount() == null) {
                req.setDebitAmount(BigDecimal.ZERO);
            }
            if (req.getCreditAmount() == null) {
                req.setCreditAmount(BigDecimal.ZERO);
            }

            if (req.getDebitAmount().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("차변 금액은 음수일 수 없습니다.");
            }
            if (req.getCreditAmount().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("대변 금액은 음수일 수 없습니다.");
            }
            if (req.getDebitAmount().compareTo(BigDecimal.ZERO) == 0
                    && req.getCreditAmount().compareTo(BigDecimal.ZERO) == 0) {
                throw new IllegalArgumentException("차변 또는 대변 중 하나는 0보다 커야 합니다.");
            }
            if (req.getDebitAmount().compareTo(BigDecimal.ZERO) > 0
                    && req.getCreditAmount().compareTo(BigDecimal.ZERO) > 0) {
                throw new IllegalArgumentException("하나의 라인에 차변과 대변 금액을 동시에 입력할 수 없습니다.");
            }

            totalDebit = totalDebit.add(req.getDebitAmount());
            totalCredit = totalCredit.add(req.getCreditAmount());
        }

        if (totalDebit.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("차변 합계가 0입니다. 유효한 분개를 입력하세요.");
        }
        if (totalCredit.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("대변 합계가 0입니다. 유효한 분개를 입력하세요.");
        }
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new IllegalArgumentException("차대 불일치: 차변(" + totalDebit + ") != 대변(" + totalCredit + ")");
        }
    }

    public void validatePeriodNotClosed(LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();

        boolean isClosed = accountingPeriodRepository
                .existsByFiscalYearAndFiscalMonthAndStatus(year, month, AccountingPeriodStatus.CLOSED);
        if (isClosed) {
            throw new IllegalStateException(
                    "마감된 회계 기간(" + year + "-" + String.format("%02d", month) + ")에는 전표를 생성할 수 없습니다.");
        }
    }

    public void validateMutable(JournalEntry entry) {
        entry.assertNotPosted();
    }
}
