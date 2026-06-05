package com.dduk.service.inventory;

import com.dduk.entity.inventory.Inventory;
import com.dduk.entity.inventory.Item;
import com.dduk.entity.inventory.MovementReason;
import com.dduk.entity.inventory.MovementType;
import com.dduk.entity.inventory.StockMovement;
import com.dduk.entity.inventory.Warehouse;
import com.dduk.repository.inventory.InventoryRepository;
import com.dduk.repository.inventory.ItemRepository;
import com.dduk.repository.inventory.StockMovementRepository;
import com.dduk.repository.inventory.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ItemRepository itemRepository;
    private final WarehouseRepository warehouseRepository;
    private final com.dduk.service.accounting.inventory.InventoryVoucherService inventoryVoucherService;

    @Transactional(rollbackFor = Exception.class)
    public void increaseStock(Long itemId, Long warehouseId, int quantity, BigDecimal unitCost, MovementReason reason, String refType, String refId) {
        Inventory inventory = getOrCreateInventory(itemId, warehouseId);
        int beforeQuantity = inventory.getCurrentStock();
        
        inventory.increaseStock(quantity, unitCost);
        inventoryRepository.save(inventory);

        String refNo = generateReferenceNo("IN");
        recordMovement(inventory.getItem(), inventory.getWarehouse(), MovementType.INBOUND, reason, refNo, quantity, unitCost, inventory.getInventoryValue(), beforeQuantity, inventory.getCurrentStock(), refType, refId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void decreaseStock(Long itemId, Long warehouseId, int quantity, MovementReason reason, String refType, String refId) {
        Inventory inventory = getInventoryOrThrow(itemId, warehouseId);
        int beforeQuantity = inventory.getCurrentStock();
        
        BigDecimal currentAvgCost = inventory.getAverageCost();
        inventory.decreaseStock(quantity);
        inventoryRepository.save(inventory);

        String refNo = generateReferenceNo("OUT");
        recordMovement(inventory.getItem(), inventory.getWarehouse(), MovementType.OUTBOUND, reason, refNo, quantity, currentAvgCost, currentAvgCost.multiply(BigDecimal.valueOf(quantity)), beforeQuantity, inventory.getCurrentStock(), refType, refId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void transferStock(Long itemId, Long fromWarehouseId, Long toWarehouseId, int quantity, String refType, String refId) {
        Inventory fromInventory = getInventoryOrThrow(itemId, fromWarehouseId);
        int fromBefore = fromInventory.getCurrentStock();
        BigDecimal transferCost = fromInventory.getAverageCost();
        
        fromInventory.decreaseStock(quantity);
        inventoryRepository.save(fromInventory);
        
        String outRefNo = generateReferenceNo("TRF-OUT");
        recordMovement(fromInventory.getItem(), fromInventory.getWarehouse(), MovementType.TRANSFER_OUT, MovementReason.TRANSFER, outRefNo, quantity, transferCost, transferCost.multiply(BigDecimal.valueOf(quantity)), fromBefore, fromInventory.getCurrentStock(), refType, refId);

        Inventory toInventory = getOrCreateInventory(itemId, toWarehouseId);
        int toBefore = toInventory.getCurrentStock();
        toInventory.increaseStock(quantity, transferCost);
        inventoryRepository.save(toInventory);
        
        String inRefNo = generateReferenceNo("TRF-IN");
        recordMovement(toInventory.getItem(), toInventory.getWarehouse(), MovementType.TRANSFER_IN, MovementReason.TRANSFER, inRefNo, quantity, transferCost, transferCost.multiply(BigDecimal.valueOf(quantity)), toBefore, toInventory.getCurrentStock(), refType, refId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void adjustStock(Long itemId, Long warehouseId, int newQuantity, MovementReason reason, String refType, String refId) {
        Inventory inventory = getOrCreateInventory(itemId, warehouseId);
        int beforeQuantity = inventory.getCurrentStock();
        int difference = newQuantity - beforeQuantity;

        if (difference == 0) return;

        MovementType type = difference > 0 ? MovementType.INBOUND : MovementType.OUTBOUND;
        BigDecimal cost = inventory.getAverageCost(); // Use current avg cost for adjustment if not provided
        
        if (difference > 0) {
            inventory.increaseStock(difference, cost);
        } else {
            inventory.decreaseStock(-difference);
        }
        
        inventoryRepository.save(inventory);
        String refNo = generateReferenceNo("ADJ");
        recordMovement(inventory.getItem(), inventory.getWarehouse(), type, reason, refNo, Math.abs(difference), cost, cost.multiply(BigDecimal.valueOf(Math.abs(difference))), beforeQuantity, newQuantity, refType, refId);
    }

    private synchronized String generateReferenceNo(String prefix) {
        String datePrefix = prefix + "-" + DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now());
        return stockMovementRepository.findTopByReferenceNoStartingWithOrderByIdDesc(datePrefix)
                .map(m -> {
                    String lastNo = m.getReferenceNo();
                    int sequence = Integer.parseInt(lastNo.substring(lastNo.length() - 4)) + 1;
                    return datePrefix + "-" + String.format("%04d", sequence);
                })
                .orElse(datePrefix + "-0001");
    }

    private Inventory getOrCreateInventory(Long itemId, Long warehouseId) {
        return inventoryRepository.findByItemIdAndWarehouseId(itemId, warehouseId)
                .orElseGet(() -> {
                    Item item = itemRepository.findById(itemId)
                            .orElseThrow(() -> new IllegalArgumentException("Item not found"));
                    Warehouse warehouse = warehouseRepository.findById(warehouseId)
                            .orElseThrow(() -> new IllegalArgumentException("Warehouse not found"));
                    return Inventory.builder()
                            .item(item)
                            .warehouse(warehouse)
                            .currentStock(0)
                            .safetyStock(0)
                            .allocatedStock(0)
                            .averageCost(BigDecimal.ZERO)
                            .inventoryValue(BigDecimal.ZERO)
                            .build();
                });
    }

    private Inventory getInventoryOrThrow(Long itemId, Long warehouseId) {
        return inventoryRepository.findByItemIdAndWarehouseId(itemId, warehouseId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory not found for item " + itemId + " in warehouse " + warehouseId));
    }

    private void recordMovement(Item item, Warehouse warehouse, MovementType type, MovementReason reason, String refNo,
                                int quantity, BigDecimal unitCost, BigDecimal totalAmount,
                                int beforeQty, int afterQty, String refType, String refId) {
        StockMovement movement = StockMovement.builder()
                .item(item)
                .warehouse(warehouse)
                .movementType(type)
                .movementReason(reason)
                .referenceNo(refNo)
                .quantity(quantity)
                .unitCost(unitCost)
                .totalAmount(totalAmount)
                .beforeQuantity(beforeQty)
                .afterQuantity(afterQty)
                .referenceType(refType)
                .referenceId(refId)
                .build();
        StockMovement savedMovement = stockMovementRepository.save(movement);
        try {
            inventoryVoucherService.createDraftVoucher(savedMovement);
        } catch (Exception e) {
            // 회계 전표 자동 생성 실패 시 로그를 남기지만, 로컬 기동 및 시드 데이터 적재 시 롤백 충돌을 방지하기 위해 로깅 처리
            log.error("Failed to create draft voucher for stock movement: {}", e.getMessage(), e);
        }
    }
}
