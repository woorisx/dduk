package com.dduk.controller.accounting;

import com.dduk.dto.accounting.JournalLineRequest;
import com.dduk.entity.accounting.Account;
import com.dduk.entity.accounting.period.AccountingPeriod;
import com.dduk.entity.accounting.JournalEntry;
import com.dduk.repository.accounting.AccountRepository;
import com.dduk.service.accounting.AccountingPeriodService;
import com.dduk.service.accounting.AccountingService;
import com.dduk.service.accounting.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/accounting")
@RequiredArgsConstructor
public class AccountingController {

    private final AccountingService accountingService;
    private final ReportService reportService;
    private final AccountRepository accountRepository;
    private final AccountingPeriodService accountingPeriodService;

    @PostMapping("/journals")
    public ResponseEntity<Map<String, Object>> createJournal(@RequestBody Map<String, Object> request) {
        LocalDate date = LocalDate.parse((String) request.get("date"));
        String description = (String) request.get("description");
        String sourceType = (String) request.getOrDefault("sourceType", null);
        Long sourceId = request.get("sourceId") != null ? Long.valueOf(request.get("sourceId").toString()) : null;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawLines = (List<Map<String, Object>>) request.get("lines");

        List<JournalLineRequest> lineRequests = new ArrayList<>();
        for (Map<String, Object> raw : rawLines) {
            JournalLineRequest req = new JournalLineRequest();
            req.setAccountCode((String) raw.get("accountCode"));
            req.setDebitAmount(raw.get("debitAmount") != null
                    ? new java.math.BigDecimal(raw.get("debitAmount").toString())
                    : java.math.BigDecimal.ZERO);
            req.setCreditAmount(raw.get("creditAmount") != null
                    ? new java.math.BigDecimal(raw.get("creditAmount").toString())
                    : java.math.BigDecimal.ZERO);
            req.setDescription((String) raw.getOrDefault("description", null));
            lineRequests.add(req);
        }

        JournalEntry entry = accountingService.createJournal(date, description, lineRequests, sourceType, sourceId);
        return success(entry, "전표가 생성되었습니다.");
    }

    @GetMapping("/journals")
    public ResponseEntity<Map<String, Object>> getJournals(
            @RequestParam(required = false) Integer fiscalYear,
            @RequestParam(required = false) Integer fiscalMonth,
            @RequestParam(required = false) String status) {
        List<JournalEntry> list;
        if (fiscalYear != null && fiscalMonth != null) {
            list = accountingService.findByFiscal(fiscalYear, fiscalMonth);
        } else if (status != null) {
            list = accountingService.findByStatus(status);
        } else {
            list = accountingService.findAll();
        }
        return success(list, "전표 목록 조회 완료");
    }

    @GetMapping("/journals/{id}")
    public ResponseEntity<Map<String, Object>> getJournal(@PathVariable Long id) {
        return success(accountingService.findById(id), "전표 조회 완료");
    }

    @PostMapping("/journals/{id}/post")
    public ResponseEntity<Map<String, Object>> postJournal(@PathVariable Long id) {
        return success(accountingService.postJournal(id), "전표가 기표되었습니다.");
    }

    @PostMapping("/journals/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelJournal(@PathVariable Long id) {
        return success(accountingService.cancelJournal(id), "전표가 취소되었습니다.");
    }

    @DeleteMapping("/journals/{id}")
    public ResponseEntity<Map<String, Object>> deleteJournal(@PathVariable Long id) {
        accountingService.deleteJournal(id);
        return success(null, "전표가 삭제되었습니다.");
    }

    @GetMapping("/accounts")
    public ResponseEntity<Map<String, Object>> getAccounts(@RequestParam(required = false) Boolean activeOnly) {
        List<Account> accounts = (activeOnly != null && activeOnly)
                ? accountRepository.findByIsActiveTrueOrderByCodeAsc()
                : accountRepository.findAll();
        return success(accounts, "계정과목 목록 조회 완료");
    }

    @GetMapping("/reports/trial-balance-legacy")
    public ResponseEntity<Map<String, Object>> getTrialBalance(
            @RequestParam(required = false) Integer fiscalYear,
            @RequestParam(required = false) Integer fiscalMonth) {
        return success(reportService.getTrialBalance(fiscalYear, fiscalMonth), "합계잔액시산표 조회 완료");
    }

    @GetMapping("/reports/general-ledger")
    public ResponseEntity<Map<String, Object>> getGeneralLedger(
            @RequestParam String accountCode,
            @RequestParam(required = false) Integer fiscalYear,
            @RequestParam(required = false) Integer fiscalMonth) {
        return success(reportService.getGeneralLedger(accountCode, fiscalYear, fiscalMonth), "총계정원장 조회 완료");
    }

    @GetMapping("/reports/profit-loss")
    public ResponseEntity<Map<String, Object>> getProfitLoss(
            @RequestParam(required = false) Integer fiscalYear,
            @RequestParam(required = false) Integer fiscalMonth) {
        return success(reportService.getProfitAndLoss(fiscalYear, fiscalMonth), "손익계산서 조회 완료");
    }

    @GetMapping("/reports/balance-sheet")
    public ResponseEntity<Map<String, Object>> getBalanceSheet(
            @RequestParam(required = false) Integer fiscalYear,
            @RequestParam(required = false) Integer fiscalMonth) {
        return success(reportService.getBalanceSheet(fiscalYear, fiscalMonth), "재무상태표 조회 완료");
    }

    @GetMapping("/periods")
    public ResponseEntity<Map<String, Object>> getPeriods() {
        return success(accountingPeriodService.getAllPeriods(), "회계기간 목록 조회 완료");
    }

    @PostMapping("/periods/{yearMonth}/close")
    public ResponseEntity<Map<String, Object>> closePeriod(
            @PathVariable String yearMonth,
            @RequestBody(required = false) Map<String, Object> body) {
        String[] parts = yearMonth.split("-");
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        String closedBy = body != null ? (String) body.getOrDefault("closedBy", "SYSTEM") : "SYSTEM";
        AccountingPeriod period = accountingPeriodService.closePeriod(year, month, closedBy);
        return success(period, year + "-" + String.format("%02d", month) + " 회계기간이 마감되었습니다.");
    }

    @PostMapping("/periods/{yearMonth}/reopen")
    public ResponseEntity<Map<String, Object>> reopenPeriod(
            @PathVariable String yearMonth,
            @RequestBody(required = false) Map<String, Object> body) {
        String[] parts = yearMonth.split("-");
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        String reopenedBy = body != null ? (String) body.getOrDefault("reopenedBy", "SYSTEM") : "SYSTEM";
        AccountingPeriod period = accountingPeriodService.reopenPeriod(year, month, reopenedBy);
        return success(period, year + "-" + String.format("%02d", month) + " 회계기간 마감이 취소되었습니다.");
    }

    private ResponseEntity<Map<String, Object>> success(Object data, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("data", data);
        response.put("message", message);
        return ResponseEntity.ok(response);
    }
}
