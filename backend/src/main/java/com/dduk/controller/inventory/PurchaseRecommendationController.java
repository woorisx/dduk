package com.dduk.controller.inventory;

import com.dduk.dto.inventory.PurchaseRecommendationDto;
import com.dduk.service.inventory.PurchaseRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class PurchaseRecommendationController {

    private final PurchaseRecommendationService purchaseRecommendationService;

    @GetMapping("/purchase-recommendations")
    public ResponseEntity<Map<String, Object>> getPurchaseRecommendations() {
        List<PurchaseRecommendationDto> items = purchaseRecommendationService.getRecommendations();
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("data", Map.of("items", items));
        response.put("message", "추천 발주 목록 조회 완료");
        return ResponseEntity.ok(response);
    }
}
