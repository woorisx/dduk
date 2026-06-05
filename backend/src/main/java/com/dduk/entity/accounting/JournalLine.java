package com.dduk.entity.accounting;

import com.dduk.entity.accounting.Account;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * 전표 라인 (journal_items 테이블 매핑)
 * - JournalEntry Aggregate 내부 구성 요소
 * - JournalEntry 를 통해서만 생성/수정
 * - 차변(debitAmount), 대변(creditAmount) 분리 방식
 */
@Entity
@Table(name = "journal_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class JournalLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    private JournalEntry journalEntry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    /** 차변 금액 (0 이상) */
    @Column(name = "debit_amount", nullable = false)
    private BigDecimal debitAmount;

    /** 대변 금액 (0 이상) */
    @Column(name = "credit_amount", nullable = false)
    private BigDecimal creditAmount;

    /** 라인 적요 */
    @Column(name = "description")
    private String description;

    /** 참조 유형 (예: PAYROLL, PURCHASE_ORDER) */
    @Column(name = "reference_type")
    private String referenceType;

    /** 참조 ID */
    @Column(name = "reference_id")
    private Long referenceId;

    // --- 패키지 내부 사용 전용 setter (Aggregate Root 경유) ---
    void setJournalEntry(JournalEntry journalEntry) {
        this.journalEntry = journalEntry;
    }

    /** 해당 라인이 차변 라인인지 여부 */
    public boolean isDebit() {
        return debitAmount != null && debitAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    /** 해당 라인이 대변 라인인지 여부 */
    public boolean isCredit() {
        return creditAmount != null && creditAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    /** 실질 금액 반환 (차변 또는 대변 중 0이 아닌 값) */
    public BigDecimal getEffectiveAmount() {
        if (isDebit()) return debitAmount;
        if (isCredit()) return creditAmount;
        return BigDecimal.ZERO;
    }
}
