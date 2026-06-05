package com.dduk.repository.accounting.taxinvoice;

import com.dduk.entity.accounting.taxinvoice.TaxInvoice;
import com.dduk.entity.accounting.taxinvoice.TaxInvoiceStatus;
import com.dduk.entity.accounting.taxinvoice.TaxInvoiceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaxInvoiceRepository extends JpaRepository<TaxInvoice, Long> {
    Optional<TaxInvoice> findByTaxInvoiceNo(String taxInvoiceNo);

    @Query("""
            SELECT DISTINCT t
            FROM TaxInvoice t
            LEFT JOIN FETCH t.lines
            WHERE t.id = :id
            """)
    Optional<TaxInvoice> findByIdWithLines(@Param("id") Long id);

    @Query("""
            SELECT DISTINCT t
            FROM TaxInvoice t
            LEFT JOIN FETCH t.lines
            WHERE (:type IS NULL OR t.taxInvoiceType = :type)
              AND (:status IS NULL OR t.status = :status)
              AND (:startDate IS NULL OR t.issueDate >= :startDate)
              AND (:endDate IS NULL OR t.issueDate <= :endDate)
              AND (:keyword IS NULL
                   OR lower(t.taxInvoiceNo) LIKE lower(concat('%', :keyword, '%'))
                   OR lower(t.supplierName) LIKE lower(concat('%', :keyword, '%'))
                   OR lower(t.recipientName) LIKE lower(concat('%', :keyword, '%'))
                   OR lower(coalesce(t.externalApprovalNo, '')) LIKE lower(concat('%', :keyword, '%')))
            ORDER BY t.issueDate DESC, t.id DESC
            """)
    List<TaxInvoice> findWithFilters(
            @Param("type") TaxInvoiceType type,
            @Param("status") TaxInvoiceStatus status,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("keyword") String keyword
    );
}
