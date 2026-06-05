package com.dduk.repository.inventory;

import com.dduk.entity.inventory.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByItemIdAndWarehouseId(Long itemId, Long warehouseId);

    // FETCH JOIN을 사용하여 LazyInitializationException 방지
    @Query("SELECT i FROM Inventory i JOIN FETCH i.item JOIN FETCH i.warehouse")
    List<Inventory> findAllWithItemAndWarehouse();

    @Query("SELECT i FROM Inventory i JOIN FETCH i.item JOIN FETCH i.warehouse WHERE i.warehouse.id = :warehouseId")
    List<Inventory> findByWarehouseIdWithFetch(@Param("warehouseId") Long warehouseId);

    @Query("SELECT i FROM Inventory i JOIN FETCH i.item JOIN FETCH i.warehouse WHERE i.item.id = :itemId")
    List<Inventory> findByItemIdWithFetch(@Param("itemId") Long itemId);

    @Query("SELECT i FROM Inventory i JOIN FETCH i.item JOIN FETCH i.warehouse WHERE i.warehouse.id = :warehouseId AND i.item.id = :itemId")
    List<Inventory> findByWarehouseIdAndItemIdWithFetch(@Param("warehouseId") Long warehouseId, @Param("itemId") Long itemId);

    @Query("SELECT i FROM Inventory i JOIN FETCH i.item JOIN FETCH i.warehouse WHERE i.currentStock <= i.safetyStock ORDER BY i.currentStock ASC")
    List<Inventory> findItemsNeedingReorderWithFetch();

    @Query("SELECT i FROM Inventory i JOIN FETCH i.item JOIN FETCH i.warehouse WHERE i.currentStock <= i.safetyStock")
    List<Inventory> findItemsNeedingReorder();

    List<Inventory> findByWarehouseId(Long warehouseId);
    List<Inventory> findByItemId(Long itemId);
    List<Inventory> findByWarehouseIdAndItemId(Long warehouseId, Long itemId);

    @Query("SELECT SUM(i.currentStock) FROM Inventory i")
    Long getTotalStockQuantity();

    @Query("SELECT SUM(i.inventoryValue) FROM Inventory i")
    BigDecimal getTotalInventoryValue();

    @Query("SELECT COUNT(i) FROM Inventory i WHERE i.currentStock <= i.safetyStock")
    Long countLowStockItems();

    @Query("SELECT COUNT(i) FROM Inventory i WHERE i.currentStock < 0 OR i.allocatedStock < 0")
    long countNegativeStockItems();

    @Query("SELECT i.warehouse.warehouseName, SUM(i.currentStock), SUM(i.inventoryValue) " +
           "FROM Inventory i GROUP BY i.warehouse.warehouseName")
    List<Object[]> getStockDistributionByWarehouse();
}
