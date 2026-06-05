package com.dduk.entity.accounting.payroll;

import com.dduk.entity.accounting.JournalEntry;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "accounting_payroll_ledgers")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PayrollLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attribution_year_month", nullable = false, length = 7)
    private String attributionYearMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "payroll_type", nullable = false, length = 30)
    private PayrollType payrollType;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_type", nullable = false, length = 30)
    private PayrollTaxType taxType;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_cycle", nullable = false, length = 30)
    private PayrollSettlementCycle settlementCycle;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_period_mode", nullable = false, length = 30)
    private PayrollTargetPeriodMode targetPeriodMode;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "payment_year_month", nullable = false, length = 7)
    private String paymentYearMonth;

    @Column(name = "ledger_name", nullable = false, length = 150)
    private String ledgerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_item_selection_mode", nullable = false, length = 20)
    private PayrollSelectionMode settlementItemSelectionMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "employee_selection_mode", nullable = false, length = 20)
    private PayrollSelectionMode employeeSelectionMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private PayrollStatus status = PayrollStatus.DRAFT;

    @Column(name = "pre_employee_checked", nullable = false)
    @Builder.Default
    private Boolean preEmployeeChecked = false;

    @Column(name = "pre_insurance_calculated", nullable = false)
    @Builder.Default
    private Boolean preInsuranceCalculated = false;

    @Column(name = "pre_settlement_validated", nullable = false)
    @Builder.Default
    private Boolean preSettlementValidated = false;

    @Column(name = "pre_account_validated", nullable = false)
    @Builder.Default
    private Boolean preAccountValidated = false;

    @Column(name = "head_count", nullable = false)
    @Builder.Default
    private Integer headCount = 0;

    @Column(name = "gross_amount", nullable = false)
    @Builder.Default
    private BigDecimal grossAmount = BigDecimal.ZERO;

    @Column(name = "deduction_amount", nullable = false)
    @Builder.Default
    private BigDecimal deductionAmount = BigDecimal.ZERO;

    @Column(name = "net_amount", nullable = false)
    @Builder.Default
    private BigDecimal netAmount = BigDecimal.ZERO;

    @Column(name = "bonus_rate_or_amount", length = 100)
    private String bonusRateOrAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id")
    private JournalEntry journalEntry;

    @OneToMany(mappedBy = "ledger", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<PayrollLedgerEmployee> employees = new ArrayList<>();

    @OneToMany(mappedBy = "ledger", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<PayrollSettlementItem> settlementItems = new ArrayList<>();

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void addEmployee(PayrollLedgerEmployee employee) {
        employees.add(employee);
        employee.setLedger(this);
    }

    public void addSettlementItem(PayrollSettlementItem item) {
        settlementItems.add(item);
        item.setLedger(this);
    }

    public void clearCalculatedEmployees() {
        employees.clear();
        grossAmount = BigDecimal.ZERO;
        deductionAmount = BigDecimal.ZERO;
        netAmount = BigDecimal.ZERO;
        headCount = 0;
    }

    public void applyTotals() {
        headCount = employees.size();
        grossAmount = employees.stream().map(PayrollLedgerEmployee::getGrossAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        deductionAmount = employees.stream().map(PayrollLedgerEmployee::getDeductionAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        netAmount = employees.stream().map(PayrollLedgerEmployee::getNetAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = PayrollStatus.DRAFT;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
