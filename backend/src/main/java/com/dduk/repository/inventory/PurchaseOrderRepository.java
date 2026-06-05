package com.dduk.repository.inventory;

import com.dduk.entity.inventory.PurchaseOrder;
import com.dduk.entity.inventory.PurchaseStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    long countByStatus(PurchaseStatus status);

    boolean existsByRequestedBy_Id(Long memberId);
    List<PurchaseOrder> findAllByOrderByCreatedAtDesc();

    @Query("""
            SELECT DISTINCT po
            FROM PurchaseOrder po
            JOIN po.vendor v
            LEFT JOIN po.requestedBy r
            WHERE LOWER(po.purchaseOrderNo) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(v.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(r.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(r.loginId) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR STR(r.id) LIKE CONCAT('%', :keyword, '%')
            ORDER BY po.createdAt DESC
            """)
    List<PurchaseOrder> searchByKeyword(@Param("keyword") String keyword);
}
