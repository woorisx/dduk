package com.dduk.entity.accounting.voucher;

import com.dduk.entity.accounting.voucher.enums.VatType;
import com.dduk.entity.accounting.voucher.enums.VoucherSourceType;
import com.dduk.entity.accounting.voucher.enums.VoucherStatus;
import com.dduk.entity.accounting.voucher.enums.VoucherType;
import com.dduk.entity.accounting.JournalEntry;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "vouchers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Voucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "voucher_no", nullable = false, unique = true)
    private String voucherNo;

    @Column(name = "voucher_date", nullable = false)
    private LocalDate voucherDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "voucher_type", nullable = false)
    private VoucherType voucherType;

    @Enumerated(EnumType.STRING)
    @Column(name = "vat_type")
    private VatType vatType;

    @Column(name = "vendor_id")
    private Long vendorId;

    @Column(name = "vendor_name_snapshot")
    private String vendorNameSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VoucherStatus status;

    /**
     * 자동 생성 원천 도메인 식별자.
     * 수동 전표(사용자 직접 입력)인 경우 null.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 50)
    private VoucherSourceType sourceType;

    /**
     * 원천 도메인 엔티티 ID.
     * STOCK_INBOUND 시 stock_movements.id, PURCHASE_ORDER 시 purchase_orders.id 등.
     * 수동 전표인 경우 null.
     */
    @Column(name = "source_reference_id")
    private Long sourceReferenceId;

    private String description;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id")
    private JournalEntry journalEntry;

    @OneToMany(mappedBy = "voucher", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, lineNo ASC")
    @Builder.Default
    private List<VoucherLine> lines = new ArrayList<>();

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void addLine(VoucherLine line) {
        lines.add(line);
        line.setVoucher(this);
    }

    public void setJournalEntry(JournalEntry journalEntry) {
        this.journalEntry = journalEntry;
    }

    public void updateStatus(VoucherStatus status) {
        this.status = status;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = VoucherStatus.DRAFT;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
