package com.dduk.entity.inventory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.dduk.entity.inventory.Warehouse;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Inventory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "location", nullable = false, length = 100)
    private String location;

    @Column(name = "current_stock", nullable = false)
    private Integer currentStock;

    @Column(name = "safety_stock", nullable = false)
    private Integer safetyStock;

    @Column(name = "allocated_stock", nullable = false)
    private Integer allocatedStock; // 예약수량

    @Column(name = "average_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal averageCost;

    @Column(name = "inventory_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal inventoryValue;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    public Integer getAvailableStock() {
        return currentStock - allocatedStock;
    }

    public void increaseStock(int amount, BigDecimal unitCost) {
        if (amount < 0) throw new IllegalArgumentException("Amount must be positive");
        
        BigDecimal totalCostOfNewItems = unitCost.multiply(BigDecimal.valueOf(amount));
        BigDecimal currentTotalValue = this.inventoryValue != null ? this.inventoryValue : BigDecimal.ZERO;
        
        int newTotalQty = this.currentStock + amount;
        BigDecimal newTotalValue = currentTotalValue.add(totalCostOfNewItems);
        
        if (newTotalQty > 0) {
            this.averageCost = newTotalValue.divide(BigDecimal.valueOf(newTotalQty), 4, RoundingMode.HALF_UP);
        }
        
        this.currentStock = newTotalQty;
        this.inventoryValue = newTotalValue;
    }

    public void decreaseStock(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Amount must be positive");
        if (this.currentStock < amount) throw new IllegalArgumentException("Insufficient stock. Current stock: " + currentStock);
        
        this.currentStock -= amount;
        // When decreasing stock, we use the current average cost to reduce the inventory value
        this.inventoryValue = this.averageCost.multiply(BigDecimal.valueOf(this.currentStock));
    }

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (location == null && warehouse != null) {
            location = warehouse.getWarehouseCode();
        }
        if (currentStock == null) currentStock = 0;
        if (safetyStock == null) safetyStock = 0;
        if (allocatedStock == null) allocatedStock = 0;
        if (averageCost == null) averageCost = BigDecimal.ZERO;
        if (inventoryValue == null) inventoryValue = BigDecimal.ZERO;
    }
}
