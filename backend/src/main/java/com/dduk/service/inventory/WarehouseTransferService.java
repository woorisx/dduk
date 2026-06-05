package com.dduk.service.inventory;

import com.dduk.dto.inventory.WarehouseTransferItemDto;
import com.dduk.dto.inventory.WarehouseTransferRequestDto;
import com.dduk.dto.inventory.WarehouseTransferResponseDto;
import com.dduk.entity.admin.Member;
import com.dduk.entity.inventory.*;
import com.dduk.repository.admin.MemberRepository;
import com.dduk.repository.inventory.*;
import com.dduk.service.accounting.inventory.InventoryVoucherService;
import com.dduk.service.accounting.period.MonthlyClosingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseTransferService {

    private final WarehouseTransferRepository warehouseTransferRepository;
    private final WarehouseTransferItemRepository warehouseTransferItemRepository;
    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final WarehouseRepository warehouseRepository;
    private final ItemRepository itemRepository;
    private final MemberRepository memberRepository;
    private final MonthlyClosingService monthlyClosingService;
    private final InventoryVoucherService inventoryVoucherService;

    @Transactional(rollbackFor = Exception.class)
    public WarehouseTransferResponseDto requestTransfer(WarehouseTransferRequestDto requestDto, Long memberId) {
        if (requestDto.getSourceWarehouseId().equals(requestDto.getTargetWarehouseId())) {
            throw new IllegalArgumentException("출발 창고와 도착 창고는 같을 수 없습니다.");
        }

        Warehouse sourceWh = warehouseRepository.findById(requestDto.getSourceWarehouseId())
                .orElseThrow(() -> new IllegalArgumentException("출발 창고를 찾을 수 없습니다."));
        Warehouse targetWh = warehouseRepository.findById(requestDto.getTargetWarehouseId())
                .orElseThrow(() -> new IllegalArgumentException("도착 창고를 찾을 수 없습니다."));
        Member requester = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("요청한 사용자를 찾을 수 없습니다."));

        String transferNo = generateTransferNo();

        WarehouseTransfer transfer = WarehouseTransfer.builder()
                .transferNo(transferNo)
                .sourceWarehouse(sourceWh)
                .targetWarehouse(targetWh)
                .status(TransferStatus.PENDING)
                .remarks(requestDto.getRemarks())
                .requestedBy(requester)
                .build();

        WarehouseTransfer savedTransfer = warehouseTransferRepository.save(transfer);

        List<WarehouseTransferItem> transferItems = requestDto.getItems().stream().map(itemDto -> {
            Item item = itemRepository.findById(itemDto.getItemId())
                    .orElseThrow(() -> new IllegalArgumentException("품목을 찾을 수 없습니다. ID: " + itemDto.getItemId()));

            // 가용 재고 검증 (현재고 - 예약재고)
            Inventory inventory = getOrCreateInventory(item.getId(), sourceWh.getId());
            int availableStock = inventory.getCurrentStock() - inventory.getAllocatedStock();
            if (availableStock < itemDto.getQuantity()) {
                throw new IllegalArgumentException(item.getName() + " 품목의 가용 재고가 부족합니다. (가용: " + availableStock + ", 요청: " + itemDto.getQuantity() + ")");
            }

            // 예약재고 allocatedStock 가산
            inventory.setAllocatedStock(inventory.getAllocatedStock() + itemDto.getQuantity());
            inventoryRepository.save(inventory);

            return WarehouseTransferItem.builder()
                    .warehouseTransfer(savedTransfer)
                    .item(item)
                    .quantity(itemDto.getQuantity())
                    .build();
        }).collect(Collectors.toList());

        warehouseTransferItemRepository.saveAll(transferItems);
        savedTransfer.setItems(transferItems);

        return convertToResponseDto(savedTransfer);
    }

    @Transactional(rollbackFor = Exception.class)
    public WarehouseTransferResponseDto approveTransfer(Long transferId, Long approverId) {
        WarehouseTransfer transfer = warehouseTransferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("이동 요청을 찾을 수 없습니다."));
        Member approver = memberRepository.findById(approverId)
                .orElseThrow(() -> new IllegalArgumentException("승인자를 찾을 수 없습니다."));

        transfer.approve(approver);
        warehouseTransferRepository.save(transfer);

        return convertToResponseDto(transfer);
    }

    @Transactional(rollbackFor = Exception.class)
    public WarehouseTransferResponseDto completeTransfer(Long transferId) {
        WarehouseTransfer transfer = warehouseTransferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("이동 요청을 찾을 수 없습니다."));

        // [1] 멱등성 보장 - 이미 COMPLETED면 그대로 반환
        if (transfer.getStatus() == TransferStatus.COMPLETED) {
            return convertToResponseDto(transfer);
        }

        // [2] 회계기간 마감 검증 (재고 이동 처리 전 초입에서 차단)
        LocalDateTime now = LocalDateTime.now();
        monthlyClosingService.assertPeriodMutable(
                java.time.LocalDate.of(now.getYear(), now.getMonthValue(), now.getDayOfMonth()));

        // [3] 상태 머신 강제
        transfer.complete();

        Warehouse sourceWh = transfer.getSourceWarehouse();
        Warehouse targetWh = transfer.getTargetWarehouse();

        for (WarehouseTransferItem transferItem : transfer.getItems()) {
            Item item = transferItem.getItem();
            int qty = transferItem.getQuantity();

            // [4] 완료 시점 가용재고 및 현재고 재검증 (안전 장치)
            Inventory sourceInventory = inventoryRepository.findByItemIdAndWarehouseId(item.getId(), sourceWh.getId())
                    .orElseThrow(() -> new IllegalStateException("출발 창고에 재고 데이터가 존재하지 않습니다."));
            
            if (sourceInventory.getCurrentStock() < qty) {
                throw new IllegalStateException(item.getName() + " 품목의 현재 재고가 부족하여 이동 완료 처리를 진행할 수 없습니다. (현재고: " + sourceInventory.getCurrentStock() + ", 요청수량: " + qty + ")");
            }

            // [5] 출발 창고 재고 감산 (current_stock 및 allocated_stock 차감)
            int beforeSourceQty = sourceInventory.getCurrentStock();
            BigDecimal sourceAvgCost = sourceInventory.getAverageCost() != null ? sourceInventory.getAverageCost() : BigDecimal.ZERO;
            
            sourceInventory.setAllocatedStock(sourceInventory.getAllocatedStock() - qty);
            sourceInventory.setCurrentStock(sourceInventory.getCurrentStock() - qty);
            // 자산가치 갱신 (단가 * 수량)
            sourceInventory.setInventoryValue(sourceAvgCost.multiply(BigDecimal.valueOf(sourceInventory.getCurrentStock())));
            inventoryRepository.save(sourceInventory);

            // [6] 출발 창고 TRANSFER_OUT 원장(StockMovement) 적재
            String outRefNo = generateMovementRefNo("TRF-OUT");
            StockMovement outMovement = recordMovement(item, sourceWh, MovementType.TRANSFER_OUT, MovementReason.TRANSFER, outRefNo, qty,
                    sourceAvgCost, sourceAvgCost.multiply(BigDecimal.valueOf(qty)), beforeSourceQty, sourceInventory.getCurrentStock(),
                    "WAREHOUSE_TRANSFER", transfer.getTransferNo());

            // [7] 도착 창고 재고 가산 및 이동평균단가(precision 4, HALF_UP) 갱신
            Inventory targetInventory = getOrCreateInventory(item.getId(), targetWh.getId());
            int beforeTargetQty = targetInventory.getCurrentStock();
            
            BigDecimal currentTargetValue = targetInventory.getInventoryValue() != null ? targetInventory.getInventoryValue() : BigDecimal.ZERO;
            BigDecimal newInboundValue = sourceAvgCost.multiply(BigDecimal.valueOf(qty));
            
            int newTargetQty = beforeTargetQty + qty;
            BigDecimal newTargetValue = currentTargetValue.add(newInboundValue);
            
            if (newTargetQty > 0) {
                // 신규평균단가 = (기존재고금액 + 입고금액) / (기존수량 + 입고수량)
                targetInventory.setAverageCost(newTargetValue.divide(BigDecimal.valueOf(newTargetQty), 4, RoundingMode.HALF_UP));
            }
            
            targetInventory.setCurrentStock(newTargetQty);
            targetInventory.setInventoryValue(newTargetValue);
            inventoryRepository.save(targetInventory);

            // [8] 도착 창고 TRANSFER_IN 원장(StockMovement) 적재
            String inRefNo = generateMovementRefNo("TRF-IN");
            StockMovement inMovement = recordMovement(item, targetWh, MovementType.TRANSFER_IN, MovementReason.TRANSFER, inRefNo, qty,
                    sourceAvgCost, newInboundValue, beforeTargetQty, targetInventory.getCurrentStock(),
                    "WAREHOUSE_TRANSFER", transfer.getTransferNo());

            // [9] 회계 전표 자동 생성 (TRANSFER_IN/OUT 은 내부 이동이므로 전표 미생성)
            // InventoryVoucherService 내부에서 TRANSFER 유형은 null 반환
            try {
                inventoryVoucherService.createDraftVoucher(outMovement);
                inventoryVoucherService.createDraftVoucher(inMovement);
            } catch (Exception e) {
                // 전표 생성 실패 시 재고 이동 트랜잭션 전체 롤백
                log.error("[WarehouseTransferService] 재고 이동 전표 자동 생성 실패. transferId={}, error={}",
                        transferId, e.getMessage());
                throw new IllegalStateException(
                        "재고 이동 완료 처리 중 회계 전표 생성에 실패했습니다. 회계 관리자에게 문의하십시오.", e);
            }
        }

        warehouseTransferRepository.save(transfer);
        return convertToResponseDto(transfer);
    }

    @Transactional(rollbackFor = Exception.class)
    public WarehouseTransferResponseDto cancelTransfer(Long transferId) {
        WarehouseTransfer transfer = warehouseTransferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("이동 요청을 찾을 수 없습니다."));

        if (transfer.getStatus() == TransferStatus.CANCELLED) {
            return convertToResponseDto(transfer);
        }

        // 상태 머신 강제 및 예약 해제
        transfer.cancel();

        Warehouse sourceWh = transfer.getSourceWarehouse();
        for (WarehouseTransferItem transferItem : transfer.getItems()) {
            Inventory sourceInventory = inventoryRepository.findByItemIdAndWarehouseId(transferItem.getItem().getId(), sourceWh.getId())
                    .orElseThrow(() -> new IllegalStateException("출발 창고에 재고 데이터가 존재하지 않습니다."));
            
            // allocatedStock 차감 해제
            sourceInventory.setAllocatedStock(Math.max(0, sourceInventory.getAllocatedStock() - transferItem.getQuantity()));
            inventoryRepository.save(sourceInventory);
        }

        warehouseTransferRepository.save(transfer);
        return convertToResponseDto(transfer);
    }

    @Transactional(rollbackFor = Exception.class)
    public WarehouseTransferResponseDto cancelTransfer(Long transferId, String reason, String type) {
        WarehouseTransfer transfer = warehouseTransferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("이동 요청을 찾을 수 없습니다."));

        if (transfer.getStatus() == TransferStatus.CANCELLED) {
            return convertToResponseDto(transfer);
        }

        // 상태 머신 강제 및 예약 해제
        transfer.cancel();

        // remarks 에 [유형] 사유 형태로 사유 누적
        if (reason != null && !reason.trim().isEmpty()) {
            String prefix = (type != null) ? type : "취소";
            transfer.setRemarks("[" + prefix + "] 사유: " + reason);
        }

        Warehouse sourceWh = transfer.getSourceWarehouse();
        for (WarehouseTransferItem transferItem : transfer.getItems()) {
            Inventory sourceInventory = inventoryRepository.findByItemIdAndWarehouseId(transferItem.getItem().getId(), sourceWh.getId())
                    .orElseThrow(() -> new IllegalStateException("출발 창고에 재고 데이터가 존재하지 않습니다."));
            
            // allocatedStock 차감 해제
            sourceInventory.setAllocatedStock(Math.max(0, sourceInventory.getAllocatedStock() - transferItem.getQuantity()));
            inventoryRepository.save(sourceInventory);
        }

        warehouseTransferRepository.save(transfer);
        return convertToResponseDto(transfer);
    }

    @Transactional(readOnly = true)
    public List<WarehouseTransferResponseDto> getAllTransfers(TransferStatus status, Long sourceWhId, Long targetWhId) {
        List<WarehouseTransfer> transfers = warehouseTransferRepository.findAll();

        if (status != null) {
            transfers = transfers.stream().filter(t -> t.getStatus() == status).collect(Collectors.toList());
        }
        if (sourceWhId != null) {
            transfers = transfers.stream()
                    .filter(t -> t.getSourceWarehouse() != null && t.getSourceWarehouse().getId().equals(sourceWhId))
                    .collect(Collectors.toList());
        }
        if (targetWhId != null) {
            transfers = transfers.stream()
                    .filter(t -> t.getTargetWarehouse() != null && t.getTargetWarehouse().getId().equals(targetWhId))
                    .collect(Collectors.toList());
        }

        // 최신 생성순 정렬
        transfers.sort((t1, t2) -> t2.getId().compareTo(t1.getId()));

        return transfers.stream().map(this::convertToResponseDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public WarehouseTransferResponseDto getTransferById(Long id) {
        WarehouseTransfer transfer = warehouseTransferRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("이동 요청을 찾을 수 없습니다. ID: " + id));
        return convertToResponseDto(transfer);
    }

    private synchronized String generateTransferNo() {
        String datePrefix = "TRF-" + DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now());
        return warehouseTransferRepository.findTopByTransferNoStartingWithOrderByIdDesc(datePrefix)
                .map(t -> {
                    String lastNo = t.getTransferNo();
                    int sequence = Integer.parseInt(lastNo.substring(lastNo.length() - 4)) + 1;
                    return datePrefix + "-" + String.format("%04d", sequence);
                })
                .orElse(datePrefix + "-0001");
    }

    private synchronized String generateMovementRefNo(String prefix) {
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

    private StockMovement recordMovement(Item item, Warehouse warehouse, MovementType type, MovementReason reason, String refNo,
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
        return stockMovementRepository.save(movement);
    }

    private WarehouseTransferResponseDto convertToResponseDto(WarehouseTransfer transfer) {
        List<WarehouseTransferItemDto> items = transfer.getItems().stream()
                .filter(item -> item.getItem() != null)
                .map(item -> WarehouseTransferItemDto.builder()
                .itemId(item.getItem().getId())
                .itemCode(item.getItem().getItemCode())
                .itemName(item.getItem().getName())
                .unit(item.getItem().getUnit())
                .quantity(item.getQuantity())
                .build()).collect(Collectors.toList());

        return WarehouseTransferResponseDto.builder()
                .id(transfer.getId())
                .transferNo(transfer.getTransferNo())
                .sourceWarehouseId(transfer.getSourceWarehouse() != null ? transfer.getSourceWarehouse().getId() : null)
                .sourceWarehouseName(transfer.getSourceWarehouse() != null ? transfer.getSourceWarehouse().getWarehouseName() : "")
                .targetWarehouseId(transfer.getTargetWarehouse() != null ? transfer.getTargetWarehouse().getId() : null)
                .targetWarehouseName(transfer.getTargetWarehouse() != null ? transfer.getTargetWarehouse().getWarehouseName() : "")
                .status(transfer.getStatus())
                .remarks(transfer.getRemarks())
                .requestedByName(transfer.getRequestedBy() != null ? transfer.getRequestedBy().getName() : "")
                .approvedByName(transfer.getApprovedBy() != null ? transfer.getApprovedBy().getName() : "")
                .createdAt(transfer.getCreatedAt())
                .approvedAt(transfer.getApprovedAt())
                .completedAt(transfer.getCompletedAt())
                .items(items)
                .build();
    }
}
