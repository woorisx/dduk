package com.dduk.entity.accounting.voucher;

import com.dduk.entity.accounting.AccountSide;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "voucher_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class VoucherLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id", nullable = false)
    private Voucher voucher;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "account_code", nullable = false)
    private String accountCode;

    @Column(name = "account_name", nullable = false)
    private String accountName;

    @Enumerated(EnumType.STRING)
    @Column(name = "debit_credit", nullable = false)
    private AccountSide debitCredit;

    @Column(name = "supply_amount", nullable = false)
    @Builder.Default
    private BigDecimal supplyAmount = BigDecimal.ZERO;

    @Column(name = "vat_amount", nullable = false)
    @Builder.Default
    private BigDecimal vatAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    private Integer quantity;
    private BigDecimal unitPrice;
    private String description;

    /**
     * 연결된 StockMovement ID (nullable).
     * 재고 입출고 자동 전표 라인에만 세팅되며, 수동전표 / 급여 / 발주전표 라인은 null입니다.
     */
    @Column(name = "stock_movement_id")
    private Long stockMovementId;

    /** 원장 거래 구분 스냅샷 (감사 추적용 - MovementType enum 이름). */
    @Column(name = "movement_type", length = 50)
    private String movementType;

    /** 원장 참조번호 스냅샷 (감사 추적용 - stock_movements.reference_no). */
    @Column(name = "movement_reference_no", length = 50)
    private String movementReferenceNo;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    void setVoucher(Voucher voucher) {
        this.voucher = voucher;
    }
}
