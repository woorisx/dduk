package com.dduk.repository.inventory;

import com.dduk.entity.inventory.PurchaseOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, Long> {
    @Query("""
            SELECT poi
            FROM PurchaseOrderItem poi
            JOIN FETCH poi.purchaseOrder po
            JOIN FETCH poi.item item
            WHERE po.orderDate >= :since
            ORDER BY po.orderDate DESC, poi.id DESC
            """)
    List<PurchaseOrderItem> findRecentLeadTimeCandidates(@Param("since") LocalDate since);
}
