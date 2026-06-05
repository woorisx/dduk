package com.dduk.entity.inventory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.dduk.entity.inventory.MovementReason;
import com.dduk.entity.inventory.MovementType;
import com.dduk.entity.inventory.Item;
import com.dduk.entity.inventory.Warehouse;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_movements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class StockMovement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false, updatable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false, updatable = false)
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, updatable = false)
    private MovementType movementType;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_reason", nullable = false, updatable = false)
    private MovementReason movementReason;

    @Column(name = "reference_no", nullable = false, unique = true, updatable = false)
    private String referenceNo;

    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "source_type", updatable = false)
    private String sourceType;

    @Column(name = "source_id", updatable = false)
    private String sourceId;

    @Column(nullable = false, updatable = false)
    private Integer quantity;

    @Column(name = "before_quantity", nullable = false, updatable = false)
    private Integer beforeQuantity;

    @Column(name = "after_quantity", nullable = false, updatable = false)
    private Integer afterQuantity;

    @Column(name = "reference_type", updatable = false)
    private String referenceType;

    @Column(name = "reference_id", updatable = false)
    private String referenceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
