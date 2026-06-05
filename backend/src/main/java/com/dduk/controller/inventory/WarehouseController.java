package com.dduk.controller.inventory;

import com.dduk.entity.inventory.Warehouse;
import com.dduk.service.inventory.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getWarehouses() {
        List<Warehouse> data = warehouseService.getAllWarehouses();
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
