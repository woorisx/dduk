package com.dduk.entity.accounting.payroll;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "accounting_payroll_settlement_items")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PayrollSettlementItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ledger_id", nullable = false)
    private PayrollLedger ledger;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_item_type", nullable = false, length = 50)
    private PayrollSettlementItemType settlementItemType;

    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "READY";
}
