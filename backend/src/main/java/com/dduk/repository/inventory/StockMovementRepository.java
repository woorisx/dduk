package com.dduk.repository.inventory;

import com.dduk.entity.inventory.MovementType;
import com.dduk.entity.inventory.StockMovement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    List<StockMovement> findByItemId(Long itemId);
    List<StockMovement> findByWarehouseId(Long warehouseId);
    boolean existsByReferenceTypeAndReferenceIdAndMovementType(String referenceType, String referenceId, MovementType movementType);
    
    Optional<StockMovement> findTopByReferenceNoStartingWithOrderByIdDesc(String prefix);

    // FETCH JOIN - LazyInitializationException 방지
    @Query("SELECT m FROM StockMovement m JOIN FETCH m.item JOIN FETCH m.warehouse ORDER BY m.createdAt DESC, m.id DESC")
    List<StockMovement> findAllWithFetch();

    @Query("SELECT m FROM StockMovement m JOIN FETCH m.item JOIN FETCH m.warehouse WHERE m.warehouse.id = :warehouseId ORDER BY m.createdAt DESC, m.id DESC")
    List<StockMovement> findByWarehouseIdWithFetch(@Param("warehouseId") Long warehouseId);

    @Query("SELECT m FROM StockMovement m JOIN FETCH m.item JOIN FETCH m.warehouse WHERE m.item.id = :itemId ORDER BY m.createdAt DESC, m.id DESC")
    List<StockMovement> findByItemIdWithFetch(@Param("itemId") Long itemId);

    @Query("SELECT m FROM StockMovement m JOIN FETCH m.item JOIN FETCH m.warehouse WHERE m.warehouse.id = :warehouseId AND m.item.id = :itemId ORDER BY m.createdAt DESC, m.id DESC")
    List<StockMovement> findByWarehouseIdAndItemIdWithFetch(@Param("warehouseId") Long warehouseId, @Param("itemId") Long itemId);

    @Query("SELECT m FROM StockMovement m JOIN FETCH m.item JOIN FETCH m.warehouse WHERE m.movementType = :movementType ORDER BY m.createdAt DESC, m.id DESC")
    List<StockMovement> findByMovementTypeWithFetch(@Param("movementType") MovementType movementType);

    @Query("SELECT m FROM StockMovement m JOIN FETCH m.item JOIN FETCH m.warehouse WHERE m.warehouse.id = :warehouseId AND m.movementType = :movementType ORDER BY m.createdAt DESC, m.id DESC")
    List<StockMovement> findByWarehouseIdAndMovementTypeWithFetch(@Param("warehouseId") Long warehouseId, @Param("movementType") MovementType movementType);

    @Query("SELECT m FROM StockMovement m JOIN FETCH m.item JOIN FETCH m.warehouse WHERE m.item.id = :itemId AND m.movementType = :movementType ORDER BY m.createdAt DESC, m.id DESC")
    List<StockMovement> findByItemIdAndMovementTypeWithFetch(@Param("itemId") Long itemId, @Param("movementType") MovementType movementType);

    @Query("SELECT SUM(m.quantity) FROM StockMovement m WHERE m.movementType = 'OUTBOUND' AND m.createdAt >= :since")
    Long getOutboundVolumeSince(LocalDateTime since);

    @Query("SELECT MAX(m.createdAt) FROM StockMovement m WHERE m.item.id = :itemId AND m.warehouse.id = :warehouseId AND m.movementType IN (com.dduk.entity.inventory.MovementType.OUTBOUND, com.dduk.entity.inventory.MovementType.TRANSFER_OUT, com.dduk.entity.inventory.MovementType.RETURN_OUT)")
    LocalDateTime findLastOutboundTime(@Param("itemId") Long itemId, @Param("warehouseId") Long warehouseId);
}
