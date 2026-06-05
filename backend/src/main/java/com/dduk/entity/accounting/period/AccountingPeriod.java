package com.dduk.entity.accounting.period;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "accounting_periods",
        uniqueConstraints = @UniqueConstraint(name = "uk_accounting_period_year_month", columnNames = {"fiscal_year", "fiscal_month"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AccountingPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;

    @Column(name = "fiscal_month", nullable = false)
    private Integer fiscalMonth;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AccountingPeriodStatus status;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closed_by", length = 80)
    private String closedBy;

    @Column(name = "reopened_at")
    private LocalDateTime reopenedAt;

    @Column(name = "reopened_by", length = 80)
    private String reopenedBy;

    @Column(name = "reopen_count", nullable = false)
    @Builder.Default
    private Integer reopenCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean isClosed() {
        return status == AccountingPeriodStatus.CLOSED || status == AccountingPeriodStatus.ARCHIVED;
    }

    public void markPreClosing() {
        if (status == AccountingPeriodStatus.CLOSED || status == AccountingPeriodStatus.ARCHIVED) {
            throw new IllegalStateException("Closed or archived accounting periods cannot enter pre-closing.");
        }
        this.status = AccountingPeriodStatus.PRE_CLOSING;
    }

    public void close(String actor) {
        if (isClosed()) {
            throw new IllegalStateException("Accounting period is already closed: " + getPeriodKey());
        }
        this.status = AccountingPeriodStatus.CLOSED;
        this.closedAt = LocalDateTime.now();
        this.closedBy = actor;
    }

    public void reopen(String actor) {
        if (status != AccountingPeriodStatus.CLOSED) {
            throw new IllegalStateException("Only CLOSED accounting periods can be reopened: " + getPeriodKey());
        }
        this.status = AccountingPeriodStatus.REOPENED;
        this.reopenedAt = LocalDateTime.now();
        this.reopenedBy = actor;
        this.reopenCount = this.reopenCount == null ? 1 : this.reopenCount + 1;
    }

    public void updateDates(LocalDate startDate, LocalDate endDate) {
        if (isClosed()) {
            throw new IllegalStateException("Closed accounting periods cannot be modified: " + getPeriodKey());
        }
        validateDateRange(startDate, endDate);
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public String getPeriodKey() {
        return fiscalYear + "-" + String.format("%02d", fiscalMonth);
    }

    @PrePersist
    protected void onCreate() {
        applyDefaultDatesIfMissing();
        validateDateRange(startDate, endDate);
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = AccountingPeriodStatus.OPEN;
        }
        if (reopenCount == null) {
            reopenCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        applyDefaultDatesIfMissing();
        validateDateRange(startDate, endDate);
        updatedAt = LocalDateTime.now();
    }

    @PostLoad
    protected void onLoad() {
        applyDefaultDatesIfMissing();
    }

    private void applyDefaultDatesIfMissing() {
        if (fiscalYear != null && fiscalMonth != null && (startDate == null || endDate == null)) {
            LocalDate firstDay = LocalDate.of(fiscalYear, fiscalMonth, 1);
            if (startDate == null) {
                startDate = firstDay;
            }
            if (endDate == null) {
                endDate = firstDay.withDayOfMonth(firstDay.lengthOfMonth());
            }
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Accounting period startDate and endDate are required.");
        }
        if (!startDate.isBefore(endDate)) {
            throw new IllegalArgumentException("Accounting period startDate must be before endDate.");
        }
    }
}
