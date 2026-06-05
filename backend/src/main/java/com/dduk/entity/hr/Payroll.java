package com.dduk.entity.hr;

import com.dduk.entity.hr.Employee;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payrolls")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payroll {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "pay_month", nullable = false, length = 7)
    private String payMonth; // YYYY-MM

    @Column(name = "base_salary", nullable = false)
    private BigDecimal baseSalary;

    @Column(name = "allowance_amount", nullable = false)
    private BigDecimal allowanceAmount;

    @Column(name = "deduction_amount", nullable = false)
    private BigDecimal deductionAmount;

    @Column(name = "net_salary", nullable = false)
    private BigDecimal netSalary;

    @Column(nullable = false)
    private String status; // DRAFT, CALCULATING, CALCULATED, PENDING_APPROVAL, APPROVED, POSTED, PAID, REVERSED, CANCELLED

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Optional: for detailed calculation trace
    @Column(columnDefinition = "TEXT")
    private String calculationTrace;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "DRAFT";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
