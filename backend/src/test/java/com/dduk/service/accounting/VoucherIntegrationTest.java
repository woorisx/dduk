package com.dduk.service.accounting;

import com.dduk.dto.accounting.period.AccountingPeriodCreateRequest;
import com.dduk.dto.accounting.voucher.VoucherRequest;
import com.dduk.dto.accounting.voucher.VoucherResponse;
import com.dduk.entity.accounting.*;
import com.dduk.entity.accounting.period.AccountingPeriod;
import com.dduk.entity.accounting.period.AccountingPeriodStatus;
import com.dduk.entity.accounting.voucher.Voucher;
import com.dduk.entity.accounting.voucher.VoucherLine;
import com.dduk.entity.accounting.voucher.enums.VatType;
import com.dduk.entity.accounting.voucher.enums.VoucherStatus;
import com.dduk.entity.accounting.voucher.enums.VoucherType;
import com.dduk.repository.accounting.AccountRepository;
import com.dduk.repository.accounting.JournalEntryRepository;
import com.dduk.repository.accounting.period.AccountingPeriodRepository;
import com.dduk.repository.accounting.voucher.VoucherRepository;
import com.dduk.service.accounting.period.MonthlyClosingService;
import com.dduk.service.accounting.voucher.VoucherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Rollback
@DisplayName("회계관리 전면 DB 연동 및 POSTED 회계 흐름 통합 검증")
public class VoucherIntegrationTest {

    @Autowired
    private VoucherService voucherService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private AccountingPeriodRepository accountingPeriodRepository;

    @Autowired
    private MonthlyClosingService monthlyClosingService;

    private Account cashAccount;            // 10101 (자산) - 입금계좌
    private Account salesRevenueAccount;     // 41001 (매출) - 매출계정
    private Account commissionExpenseAccount; // 5370 (비용)  - 카드수수료
    private Account vatLiabilityAccount;     // 2128 (부채)  - 부가세예수금

    @BeforeEach
    void setUp() {
        // 1. 테스트용 2026-05 OPEN 회계기간 설정 (이미 존재하면 OPEN 상태인지 확인, 없으면 생성)
        LocalDate date = LocalDate.of(2026, 5, 26);
        Optional<AccountingPeriod> existingPeriod = accountingPeriodRepository.findByFiscalYearAndFiscalMonth(2026, 5);
        if (existingPeriod.isEmpty()) {
            AccountingPeriodCreateRequest periodRequest = new AccountingPeriodCreateRequest();
            periodRequest.setFiscalYear(2026);
            periodRequest.setFiscalMonth(5);
            periodRequest.setStartDate(LocalDate.of(2026, 5, 1));
            periodRequest.setEndDate(LocalDate.of(2026, 5, 31));
            periodRequest.setCreatedBy("TEST_SYSTEM");
            monthlyClosingService.createPeriod(periodRequest);
        } else {
            AccountingPeriod period = existingPeriod.get();
            if (period.getStatus() != AccountingPeriodStatus.OPEN) {
                // 강제로 OPEN 상태로 변경 (테스트의 편의를 위해)
                try {
                    var field = AccountingPeriod.class.getDeclaredField("status");
                    field.setAccessible(true);
                    field.set(period, AccountingPeriodStatus.OPEN);
                    accountingPeriodRepository.saveAndFlush(period);
                } catch (Exception ignored) {}
            }
        }

        // 2. 테스트용 계정과목 셋업
        cashAccount = getOrCreateAccount("10101", "보통예금", AccountType.ASSET, AccountSide.DEBIT, true);
        salesRevenueAccount = getOrCreateAccount("41001", "상품매출", AccountType.REVENUE, AccountSide.CREDIT, true);
        commissionExpenseAccount = getOrCreateAccount("5370", "지급수수료", AccountType.EXPENSE, AccountSide.DEBIT, true);
        vatLiabilityAccount = getOrCreateAccount("2128", "부가세예수금", AccountType.LIABILITY, AccountSide.CREDIT, true);
    }

    private Account getOrCreateAccount(String code, String name, AccountType type, AccountSide side, boolean allowPosting) {
        try {
            return accountRepository.findByCode(code).orElseGet(() -> {
                Account account = Account.builder()
                        .code(code)
                        .name(name)
                        .englishName(name)
                        .type(type)
                        .normalBalance(side)
                        .level(3)
                        .sortOrder(1)
                        .status(AccountStatus.ACTIVE)
                        .allowPosting(allowPosting)
                        .systemAccount(true)
                        .deleted(false)
                        .children(new ArrayList<>())
                        .build();
                return accountRepository.saveAndFlush(account);
            });
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    @Test
    @DisplayName("매출 전표 생성부터 POSTED 기표, 월 마감 완료까지의 회계 흐름 검증")
    void verifyAccountingFlow() {
        System.out.println("==================================================================");
        System.out.println("1. 실제 DB 저장 검증: 전표 생성 (Voucher Status -> DRAFT)");
        System.out.println("==================================================================");

        VoucherRequest request = new VoucherRequest();
        request.setVoucherDate(LocalDate.of(2026, 5, 26));
        request.setVoucherType(VoucherType.SALES);
        request.setVatType(VatType.TAX_INVOICE);
        request.setVendorId(1L);
        request.setVendorNameSnapshot("테스트 거래처");
        request.setDescription("5월 상품 매출 등록 및 자동 분개 테스트");
        request.setSupplyAmount(new BigDecimal("100000")); // 공급가액: 100,000원
        request.setVatAmount(new BigDecimal("10000"));     // 부가세: 10,000원
        request.setFeeAmount(new BigDecimal("3000"));       // PG수수료: 3,000원
        request.setBusinessAccountId(salesRevenueAccount.getId());
        request.setSettlementAccountId(cashAccount.getId());

        // 1) 전표 생성 및 저장 검증
        VoucherResponse response = voucherService.createVoucher(request);
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(VoucherStatus.DRAFT);
        assertThat(response.getVoucherNo()).startsWith("V20260526-");

        // 2) DB에서 Voucher와 VoucherLines가 제대로 로드되는지 확인
        Voucher savedVoucher = voucherRepository.findById(response.getId())
                .orElseThrow(() -> new AssertionError("Voucher가 DB에 저장되지 않았습니다."));
        assertThat(savedVoucher.getLines()).hasSize(4);

        System.out.println("[Voucher DB 저장 확인]");
        System.out.println("전표번호: " + savedVoucher.getVoucherNo());
        System.out.println("일자: " + savedVoucher.getVoucherDate());
        System.out.println("유형: " + savedVoucher.getVoucherType());
        System.out.println("적요: " + savedVoucher.getDescription());
        System.out.println("거래처: " + savedVoucher.getVendorNameSnapshot());
        System.out.println("상태: " + savedVoucher.getStatus());

        System.out.println("[Voucher Lines 자동 분개 결과]");
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (VoucherLine line : savedVoucher.getLines()) {
            System.out.printf("  - 라인 %d | 계정: [%s] %s | %s | 금액: %s | 적요: %s\n",
                    line.getLineNo(), line.getAccountCode(), line.getAccountName(),
                    line.getDebitCredit(), line.getTotalAmount(), line.getDescription());

            if (line.getDebitCredit() == AccountSide.DEBIT) {
                totalDebit = totalDebit.add(line.getTotalAmount());
            } else {
                totalCredit = totalCredit.add(line.getTotalAmount());
            }
        }
        System.out.println("-> 차변 합계: " + totalDebit + "원 / 대변 합계: " + totalCredit + "원");
        assertThat(totalDebit).isEqualByComparingTo(totalCredit); // 차대 합계 검증
        assertThat(totalDebit).isEqualByComparingTo(new BigDecimal("110000")); // 107,000(예금) + 3,000(수수료) = 110,000

        System.out.println("==================================================================");
        System.out.println("2. POSTED 회계 흐름 검증: DRAFT -> REQUESTED -> APPROVED -> POSTED");
        System.out.println("==================================================================");

        // 3) DRAFT -> REQUESTED
        voucherService.updateStatus(savedVoucher.getId(), VoucherStatus.REQUESTED);
        assertThat(savedVoucher.getStatus()).isEqualTo(VoucherStatus.REQUESTED);
        System.out.println("상태 변경: DRAFT -> REQUESTED 완료");

        // 4) REQUESTED -> APPROVED
        voucherService.updateStatus(savedVoucher.getId(), VoucherStatus.APPROVED);
        assertThat(savedVoucher.getStatus()).isEqualTo(VoucherStatus.APPROVED);
        System.out.println("상태 변경: REQUESTED -> APPROVED 완료");

        // 5) APPROVED -> POSTED
        voucherService.updateStatus(savedVoucher.getId(), VoucherStatus.POSTED);
        assertThat(savedVoucher.getStatus()).isEqualTo(VoucherStatus.POSTED);
        System.out.println("상태 변경: APPROVED -> POSTED 완료");

        // 6) POSTED 상태가 되었을 때, JournalEntry와 JournalLines가 POSTED 되었는지 확인
        JournalEntry journalEntry = savedVoucher.getJournalEntry();
        assertThat(journalEntry).isNotNull();
        assertThat(journalEntry.getStatus()).isEqualTo("POSTED");

        System.out.println("[JournalEntry DB 저장 및 기표(POSTED) 확인]");
        System.out.println("분개번호: " + journalEntry.getJournalNo());
        System.out.println("일자: " + journalEntry.getTransactionDate());
        System.out.println("상태: " + journalEntry.getStatus());
        System.out.println("총 차변금액: " + journalEntry.getTotalDebit());
        System.out.println("총 대변금액: " + journalEntry.getTotalCredit());

        System.out.println("[Journal Lines 세부 분개 목록]");
        for (JournalLine line : journalEntry.getLines()) {
            System.out.printf("  - 계정: [%s] %s | 차변: %s | 대변: %s | 적요: %s\n",
                    line.getAccount().getCode(), line.getAccount().getName(),
                    line.getDebitAmount(), line.getCreditAmount(), line.getDescription());
        }

        assertThat(journalEntry.getTotalDebit()).isEqualByComparingTo(journalEntry.getTotalCredit());
        assertThat(journalEntry.getTotalDebit()).isEqualByComparingTo(new BigDecimal("110000"));

        System.out.println("==================================================================");
        System.out.println("3. 월 마감 검증: 마감 검증 실행 및 마감");
        System.out.println("==================================================================");

        // 7) 월 마감 검증 실행
        var validationResponse = monthlyClosingService.validatePeriod(2026, 5, null);
        System.out.println("월 마감 검증 결과:");
        System.out.println("  - 회계기간: " + validationResponse.getPeriodKey());
        System.out.println("  - 검증 상태: " + validationResponse.getOverallStatus());
        System.out.println("  - 마감 가능 여부: " + validationResponse.isClosable());

        // 8) 월 마감 처리 실행
        com.dduk.dto.accounting.period.ClosingActionRequest closingRequest = new com.dduk.dto.accounting.period.ClosingActionRequest();
        closingRequest.setForce(true);
        closingRequest.setActor("TEST_SYSTEM");
        var closingResponse = monthlyClosingService.closePeriod(2026, 5, closingRequest);
        assertThat(closingResponse.getStatus()).isEqualTo(AccountingPeriodStatus.CLOSED);

        AccountingPeriod closedPeriod = accountingPeriodRepository.findByFiscalYearAndFiscalMonth(2026, 5)
                .orElseThrow();
        System.out.println("[Monthly Closing 테이블 저장 결과]");
        System.out.println("회계기간 Key: " + closedPeriod.getPeriodKey());
        System.out.println("상태: " + closedPeriod.getStatus());
        System.out.println("마감일시: " + closedPeriod.getClosedAt());
        System.out.println("마감자: " + closedPeriod.getClosedBy());

        System.out.println("==================================================================");
        System.out.println("====== DB 저장 검증 및 POSTED 회계 흐름 검증 완료 ======");
        System.out.println("==================================================================");
    }
}
