package com.dduk.entity.accounting.payroll;

import com.dduk.entity.hr.Employee;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "accounting_payroll_ledger_employees")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PayrollLedgerEmployee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ledger_id", nullable = false)
    private PayrollLedger ledger;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "employee_no_snapshot", nullable = false, length = 50)
    private String employeeNoSnapshot;

    @Column(name = "employee_name_snapshot", nullable = false, length = 100)
    private String employeeNameSnapshot;

    @Column(name = "department_snapshot", length = 100)
    private String departmentSnapshot;

    @Column(name = "position_snapshot", length = 100)
    private String positionSnapshot;

    @Column(name = "bank_account_snapshot", length = 120)
    private String bankAccountSnapshot;

    @Column(name = "gross_amount", nullable = false)
    @Builder.Default
    private BigDecimal grossAmount = BigDecimal.ZERO;

    @Column(name = "deduction_amount", nullable = false)
    @Builder.Default
    private BigDecimal deductionAmount = BigDecimal.ZERO;

    @Column(name = "net_amount", nullable = false)
    @Builder.Default
    private BigDecimal netAmount = BigDecimal.ZERO;

    @Column(name = "payment_status", nullable = false, length = 30)
    @Builder.Default
    private String paymentStatus = "READY";

    @OneToMany(mappedBy = "ledgerEmployee", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    @Builder.Default
    private List<PayrollItem> payItems = new ArrayList<>();

    @OneToMany(mappedBy = "ledgerEmployee", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    @Builder.Default
    private List<PayrollDeductionItem> deductionItems = new ArrayList<>();

    public void addPayItem(PayrollItem item) {
        payItems.add(item);
        item.setLedgerEmployee(this);
    }

    public void addDeductionItem(PayrollDeductionItem item) {
        deductionItems.add(item);
        item.setLedgerEmployee(this);
    }

    public void applyTotals() {
        grossAmount = payItems.stream().map(PayrollItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        deductionAmount = deductionItems.stream().map(PayrollDeductionItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        netAmount = grossAmount.subtract(deductionAmount);
    }
}
