package com.dduk.service.accounting;

import com.dduk.dto.accounting.JournalLineRequest;
import com.dduk.entity.accounting.Account;
import com.dduk.entity.accounting.JournalEntry;
import com.dduk.entity.accounting.JournalLine;
import com.dduk.repository.accounting.AccountRepository;
import com.dduk.repository.accounting.JournalEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AccountingService {

    private final JournalEntryRepository journalEntryRepository;
    private final AccountRepository accountRepository;
    private final JournalValidationService journalValidationService;

    @Transactional(readOnly = true)
    public JournalEntry findById(Long id) {
        return journalEntryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("전표를 찾을 수 없습니다. id=" + id));
    }

    @Transactional(readOnly = true)
    public List<JournalEntry> findAll() {
        return journalEntryRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<JournalEntry> findByFiscal(int year, int month) {
        return journalEntryRepository.findByFiscalYearAndFiscalMonth(year, month);
    }

    @Transactional(readOnly = true)
    public List<JournalEntry> findByStatus(String status) {
        return journalEntryRepository.findByStatus(status);
    }

    @Transactional
    public JournalEntry createJournal(LocalDate date, String description,
                                      List<JournalLineRequest> lineRequests,
                                      String sourceType, Long sourceId) {
        journalValidationService.validatePeriodNotClosed(date);
        journalValidationService.validateLines(lineRequests);

        if (sourceType != null && sourceId != null
                && journalEntryRepository.existsBySourceTypeAndSourceId(sourceType, sourceId)) {
            throw new IllegalStateException("이미 해당 원천에 대한 전표가 존재합니다: " + sourceType + " #" + sourceId);
        }

        JournalEntry entry = JournalEntry.builder()
                .journalNo(generateJournalNo())
                .transactionDate(date)
                .description(description)
                .status(AccountingConstants.JOURNAL_STATUS_DRAFT)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .totalDebit(BigDecimal.ZERO)
                .totalCredit(BigDecimal.ZERO)
                .build();

        for (JournalLineRequest req : lineRequests) {
            Account account = accountRepository.findByCode(req.getAccountCode())
                    .orElseThrow(() -> new IllegalArgumentException("계정코드를 찾을 수 없습니다: " + req.getAccountCode()));

            JournalLine line = JournalLine.builder()
                    .account(account)
                    .debitAmount(req.getDebitAmount() != null ? req.getDebitAmount() : BigDecimal.ZERO)
                    .creditAmount(req.getCreditAmount() != null ? req.getCreditAmount() : BigDecimal.ZERO)
                    .description(req.getDescription())
                    .referenceType(req.getReferenceType())
                    .referenceId(req.getReferenceId())
                    .build();
            entry.addLine(line);
        }

        return journalEntryRepository.save(entry);
    }

    @Transactional
    public JournalEntry postJournal(Long id) {
        JournalEntry entry = findById(id);
        journalValidationService.validatePeriodNotClosed(entry.getTransactionDate());
        entry.post();
        return journalEntryRepository.save(entry);
    }

    @Transactional
    public JournalEntry cancelJournal(Long id) {
        JournalEntry entry = findById(id);
        journalValidationService.validatePeriodNotClosed(entry.getTransactionDate());
        entry.cancel();
        return journalEntryRepository.save(entry);
    }

    @Transactional
    public void deleteJournal(Long id) {
        JournalEntry entry = findById(id);
        journalValidationService.validatePeriodNotClosed(entry.getTransactionDate());

        if (!AccountingConstants.JOURNAL_STATUS_DRAFT.equals(entry.getStatus())) {
            throw new IllegalStateException("DRAFT 상태의 전표만 삭제할 수 있습니다.");
        }
        journalEntryRepository.delete(entry);
    }

    @Transactional
    public JournalEntry createAndPostJournal(LocalDate date, String description,
                                             List<JournalLineRequest> lineRequests,
                                             String sourceType, Long sourceId) {
        JournalEntry entry = createJournal(date, description, lineRequests, sourceType, sourceId);
        entry.post();
        return journalEntryRepository.save(entry);
    }

    @Transactional
    public JournalEntry createAndPost(LocalDate date, String description,
                                      List<Map<String, Object>> items,
                                      String sourceType, Long sourceId) {
        List<JournalLineRequest> lineRequests = new ArrayList<>();
        for (Map<String, Object> item : items) {
            JournalLineRequest req = new JournalLineRequest();
            req.setAccountCode((String) item.get("accountCode"));
            Object amt = item.get("amount");
            BigDecimal amount = new BigDecimal(amt.toString());
            String side = (String) item.get("side");
            if ("DEBIT".equals(side)) {
                req.setDebitAmount(amount);
                req.setCreditAmount(BigDecimal.ZERO);
            } else {
                req.setDebitAmount(BigDecimal.ZERO);
                req.setCreditAmount(amount);
            }
            lineRequests.add(req);
        }
        return createAndPostJournal(date, description, lineRequests, sourceType, sourceId);
    }

    private String generateJournalNo() {
        return "JRN-" + System.currentTimeMillis();
    }
}
