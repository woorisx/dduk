package com.dduk.service.inventory;

import com.dduk.entity.inventory.Inventory;
import com.dduk.entity.inventory.MovementReason;
import com.dduk.entity.inventory.MovementType;
import com.dduk.entity.inventory.StockMovement;
import com.dduk.repository.inventory.InventoryRepository;
import com.dduk.repository.inventory.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class InventoryRebuildService {

    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final InventoryValidationService validationService;

    @Transactional(rollbackFor = Exception.class)
    public void rebuildInventories(Long warehouseId, Long itemId) {
        // First get discrepancies
        var discrepancies = validationService.validateInventories(warehouseId, itemId);
        
        for (var dto : discrepancies) {
            Inventory inv = inventoryRepository.findById(dto.getInventoryId())
                    .orElseThrow(() -> new IllegalStateException("Inventory not found"));

            int oldStock = inv.getCurrentStock();
            inv.setCurrentStock(dto.getCalculatedStock());
            inventoryRepository.save(inv);

            // Create an adjustment movement to reflect the rebuild mathematically
            int difference = dto.getCalculatedStock() - oldStock;
            MovementType type = difference > 0 ? MovementType.INBOUND : MovementType.OUTBOUND;
            BigDecimal cost = inv.getAverageCost();
            
            StockMovement rebuildMovement = StockMovement.builder()
                    .item(inv.getItem())
                    .warehouse(inv.getWarehouse())
                    .movementType(type)
                    .movementReason(MovementReason.REBUILD_ADJUSTMENT)
                    .referenceNo("REBUILD-" + System.currentTimeMillis())
                    .unitCost(cost)
                    .totalAmount(cost.multiply(BigDecimal.valueOf(Math.abs(difference))))
                    .quantity(Math.abs(difference))
                    .beforeQuantity(oldStock)
                    .afterQuantity(dto.getCalculatedStock())
                    .referenceType("SYSTEM_REBUILD")
                    .referenceId(null)
                    .build();
            
            stockMovementRepository.save(rebuildMovement);
        }
    }
}
