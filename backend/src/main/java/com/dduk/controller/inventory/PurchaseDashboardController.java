package com.dduk.controller.inventory;

import com.dduk.service.inventory.PurchaseDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/inventory/purchase-dashboard")
@RequiredArgsConstructor
public class PurchaseDashboardController {

    private final PurchaseDashboardService purchaseDashboardService;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("data", purchaseDashboardService.getStats());
        response.put("message", "구매/발주 대시보드 조회 완료");
        return ResponseEntity.ok(response);
    }
}
