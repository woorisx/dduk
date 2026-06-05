package com.dduk.entity.accounting;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "journal_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "journal_no", nullable = false, unique = true)
    private String journalNo;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String status;

    @Column(name = "source_type")
    private String sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "total_debit", nullable = false)
    private BigDecimal totalDebit;

    @Column(name = "total_credit", nullable = false)
    private BigDecimal totalCredit;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "fiscal_year")
    private Integer fiscalYear;

    @Column(name = "fiscal_month")
    private Integer fiscalMonth;

    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<JournalLine> lines = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void addLine(JournalLine line) {
        assertNotPosted();
        lines.add(line);
        line.setJournalEntry(this);
        recalculateTotals();
    }

    public void post() {
        if (!"DRAFT".equals(this.status)) {
            throw new IllegalStateException("DRAFT 상태의 전표만 기표할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = "POSTED";
    }

    public void cancel() {
        if (!"POSTED".equals(this.status)) {
            throw new IllegalStateException("POSTED 상태의 전표만 취소할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = "CANCELLED";
    }

    public void assertNotPosted() {
        if ("POSTED".equals(this.status) || "CANCELLED".equals(this.status)) {
            throw new IllegalStateException("기표된 전표(POSTED/CANCELLED)는 수정할 수 없습니다.");
        }
    }

    public List<JournalLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    private void recalculateTotals() {
        this.totalDebit = lines.stream()
                .map(JournalLine::getDebitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.totalCredit = lines.stream()
                .map(JournalLine::getCreditAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = "DRAFT";
        }
        if (totalDebit == null) {
            totalDebit = BigDecimal.ZERO;
        }
        if (totalCredit == null) {
            totalCredit = BigDecimal.ZERO;
        }
        if (transactionDate != null) {
            fiscalYear = transactionDate.getYear();
            fiscalMonth = transactionDate.getMonthValue();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
