package com.dduk.controller.inventory;

import com.dduk.entity.inventory.MovementReason;
import com.dduk.entity.inventory.MovementType;
import com.dduk.service.inventory.InventoryQueryService;
import com.dduk.service.inventory.InventoryRebuildService;
import com.dduk.service.inventory.InventoryService;
import com.dduk.service.inventory.InventoryValidationService;
import com.dduk.dto.inventory.InboundRequest;
import com.dduk.dto.inventory.OutboundRequest;
import com.dduk.dto.inventory.TransferRequest;
import com.dduk.dto.inventory.ValidationResultDto;
import com.dduk.entity.inventory.Inventory;
import com.dduk.entity.inventory.StockMovement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/inventories", "/api/v1/inventory"})
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryQueryService inventoryQueryService;
    private final InventoryValidationService inventoryValidationService;
    private final InventoryRebuildService inventoryRebuildService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getInventories(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long itemId,
            @RequestParam(required = false) Boolean lowStockOnly) {

        List<Inventory> data = inventoryQueryService.getInventories(warehouseId, itemId, lowStockOnly);
        return buildSuccessResponse(data);
    }

    @GetMapping({"/stock-movements", "/movements"})
    public ResponseEntity<Map<String, Object>> getStockMovements(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long itemId,
            @RequestParam(required = false) MovementType movementType) {

        List<StockMovement> data = inventoryQueryService.getStockMovements(warehouseId, itemId, movementType);
        return buildSuccessResponse(data);
    }

    @PostMapping("/transfer")
    public ResponseEntity<Map<String, Object>> transferStock(@RequestBody TransferRequest request) {
        inventoryService.transferStock(
                request.getItemId(),
                request.getFromWarehouseId(),
                request.getToWarehouseId(),
                request.getQuantity(),
                request.getReferenceType(),
                request.getReferenceId()
        );
        return buildSuccessResponse("Transfer completed successfully");
    }

    @PostMapping("/inbound")
    public ResponseEntity<Map<String, Object>> inboundStock(@RequestBody InboundRequest request) {
        inventoryService.increaseStock(
                request.getItemId(),
                request.getWarehouseId(),
                request.getQuantity(),
                request.getUnitCost(),
                request.getReason() != null ? request.getReason() : MovementReason.PURCHASE_RECEIVED,
                request.getReferenceType(),
                request.getReferenceId()
        );
        return buildSuccessResponse("Inbound completed successfully");
    }

    @PostMapping("/outbound")
    public ResponseEntity<Map<String, Object>> outboundStock(@RequestBody OutboundRequest request) {
        inventoryService.decreaseStock(
                request.getItemId(),
                request.getWarehouseId(),
                request.getQuantity(),
                request.getReason() != null ? request.getReason() : MovementReason.SALES_SHIPPED,
                request.getReferenceType(),
                request.getReferenceId()
        );
        return buildSuccessResponse("Outbound completed successfully");
    }

    @GetMapping("/reorder-recommendations")
    public ResponseEntity<Map<String, Object>> getReorderRecommendations() {
        List<Inventory> data = inventoryQueryService.getReorderRecommendations();
        return buildSuccessResponse(data);
    }

    @GetMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateInventories(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long itemId) {
        List<ValidationResultDto> data = inventoryValidationService.validateInventories(warehouseId, itemId);
        return buildSuccessResponse(data);
    }

    @PostMapping("/rebuild")
    public ResponseEntity<Map<String, Object>> rebuildInventories(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long itemId) {
        inventoryRebuildService.rebuildInventories(warehouseId, itemId);
        return buildSuccessResponse("Rebuild completed successfully");
    }

    private ResponseEntity<Map<String, Object>> buildSuccessResponse(Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("data", data);
        response.put("message", "요청이 완료되었습니다.");
        return ResponseEntity.ok(response);
    }
}
