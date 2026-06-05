package com.dduk.controller.inventory;

import com.dduk.service.inventory.InventoryQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/inventory/dashboard")
@RequiredArgsConstructor
public class InventoryDashboardController {

    private final InventoryQueryService inventoryQueryService;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        com.dduk.dto.inventory.InventoryDashboardResponseDto data = inventoryQueryService.getDashboardStats();
        return buildSuccessResponse(data);
    }

    private ResponseEntity<Map<String, Object>> buildSuccessResponse(Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("data", data);
        response.put("message", "요청이 완료되었습니다.");
        return ResponseEntity.ok(response);
    }
}
