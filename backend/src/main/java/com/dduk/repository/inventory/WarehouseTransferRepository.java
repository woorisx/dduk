package com.dduk.repository.inventory;

import com.dduk.entity.inventory.TransferStatus;
import com.dduk.entity.inventory.WarehouseTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface WarehouseTransferRepository extends JpaRepository<WarehouseTransfer, Long> {
    List<WarehouseTransfer> findByStatus(TransferStatus status);
    List<WarehouseTransfer> findBySourceWarehouseId(Long sourceWarehouseId);
    List<WarehouseTransfer> findByTargetWarehouseId(Long targetWarehouseId);
    
    Optional<WarehouseTransfer> findTopByTransferNoStartingWithOrderByIdDesc(String prefix);

    @Query("SELECT COUNT(t) FROM WarehouseTransfer t WHERE t.status = 'PENDING'")
    Long countPendingTransfers();
}
