package com.dduk.entity.accounting.period;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "accounting_closing_logs", indexes = {
        @Index(name = "idx_closing_logs_period", columnList = "accounting_period_id"),
        @Index(name = "idx_closing_logs_action_at", columnList = "action_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ClosingLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accounting_period_id", nullable = false)
    private AccountingPeriod accountingPeriod;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 40)
    private ClosingActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private AccountingPeriodStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 30)
    private AccountingPeriodStatus toStatus;

    @Column(nullable = false, length = 80)
    private String actor;

    @Column(name = "action_at", nullable = false)
    private LocalDateTime actionAt;

    @Column(name = "ip_address", length = 80)
    private String ipAddress;

    @Column(length = 1000)
    private String message;

    @PrePersist
    protected void onCreate() {
        if (actionAt == null) {
            actionAt = LocalDateTime.now();
        }
    }
}
