package com.dduk.repository.inventory;

import com.dduk.entity.inventory.WarehouseTransferItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WarehouseTransferItemRepository extends JpaRepository<WarehouseTransferItem, Long> {
    List<WarehouseTransferItem> findByWarehouseTransferId(Long transferId);
}
