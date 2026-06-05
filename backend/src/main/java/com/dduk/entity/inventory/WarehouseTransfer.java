package com.dduk.entity.inventory;

import com.dduk.entity.admin.Member;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "warehouse_transfers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class WarehouseTransfer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_no", nullable = false, unique = true, length = 50)
    private String transferNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_warehouse_id", nullable = false)
    private Warehouse sourceWarehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_warehouse_id", nullable = false)
    private Warehouse targetWarehouse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private TransferStatus status = TransferStatus.PENDING;

    @Column(length = 255)
    private String remarks;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_id", nullable = false)
    private Member requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_id")
    private Member approvedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "warehouseTransfer", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<WarehouseTransferItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = TransferStatus.PENDING;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void approve(Member approver) {
        if (this.status != TransferStatus.PENDING) {
            throw new IllegalStateException("승인 가능한 상태가 아닙니다. 현재 상태: " + this.status);
        }
        this.status = TransferStatus.APPROVED;
        this.approvedBy = approver;
        this.approvedAt = LocalDateTime.now();
    }

    public void complete() {
        if (this.status == TransferStatus.COMPLETED) {
            return; // 멱등성 보장
        }
        if (this.status != TransferStatus.APPROVED) {
            throw new IllegalStateException("이동 완료 처리는 승인된 상태에서만 가능합니다. 현재 상태: " + this.status);
        }
        this.status = TransferStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void cancel() {
        if (this.status == TransferStatus.COMPLETED || this.status == TransferStatus.CANCELLED) {
            throw new IllegalStateException("이미 완료되었거나 취소된 이동 건은 취소할 수 없습니다. 현재 상태: " + this.status);
        }
        this.status = TransferStatus.CANCELLED;
    }
}
