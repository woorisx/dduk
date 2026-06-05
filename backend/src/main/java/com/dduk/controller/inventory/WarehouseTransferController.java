package com.dduk.controller.inventory;

import com.dduk.config.PrincipalDetails;
import com.dduk.dto.inventory.WarehouseTransferRequestDto;
import com.dduk.dto.inventory.WarehouseTransferResponseDto;
import com.dduk.entity.inventory.TransferStatus;
import com.dduk.service.inventory.WarehouseTransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/warehouse-transfers")
@RequiredArgsConstructor
public class WarehouseTransferController {

    private final WarehouseTransferService warehouseTransferService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> requestTransfer(
            @RequestBody WarehouseTransferRequestDto requestDto,
            @AuthenticationPrincipal PrincipalDetails principalDetails) {
        try {
            Long memberId = principalDetails != null ? principalDetails.getMember().getId() : 1L; // Fallback to 1L if not auth (dev seed)
            WarehouseTransferResponseDto data = warehouseTransferService.requestTransfer(requestDto, memberId);
            return buildSuccessResponse(data, "창고 이동 요청이 등록되었습니다.");
        } catch (IllegalArgumentException e) {
            return buildErrorResponse(e.getMessage(), "BAD_REQUEST", HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            return buildErrorResponse("이동 요청 등록 중 서버 내부 오류가 발생했습니다.", "SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getTransfers(
            @RequestParam(required = false) TransferStatus status,
            @RequestParam(required = false) Long sourceWarehouseId,
            @RequestParam(required = false) Long targetWarehouseId) {
        try {
            List<WarehouseTransferResponseDto> data = warehouseTransferService.getAllTransfers(status, sourceWarehouseId, targetWarehouseId);
            return buildSuccessResponse(data, "조회가 완료되었습니다.");
        } catch (Exception e) {
            return buildErrorResponse("이동 목록 조회 중 서버 내부 오류가 발생했습니다.", "SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getTransferDetail(@PathVariable Long id) {
        try {
            WarehouseTransferResponseDto data = warehouseTransferService.getTransferById(id);
            return buildSuccessResponse(data, "조회가 완료되었습니다.");
        } catch (IllegalArgumentException e) {
            return buildErrorResponse(e.getMessage(), "NOT_FOUND", HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            return buildErrorResponse("이동 상세 조회 중 서버 내부 오류가 발생했습니다.", "SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveTransfer(
            @PathVariable Long id,
            @AuthenticationPrincipal PrincipalDetails principalDetails) {
        try {
            Long memberId = principalDetails != null ? principalDetails.getMember().getId() : 1L;
            WarehouseTransferResponseDto data = warehouseTransferService.approveTransfer(id, memberId);
            return buildSuccessResponse(data, "창고 이동 요청이 승인되었습니다.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            return buildErrorResponse(e.getMessage(), "INVALID_STATE_TRANSITION", HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            return buildErrorResponse("승인 처리 중 서버 내부 오류가 발생했습니다.", "SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<Map<String, Object>> completeTransfer(@PathVariable Long id) {
        try {
            WarehouseTransferResponseDto data = warehouseTransferService.completeTransfer(id);
            return buildSuccessResponse(data, "창고 이동이 정상 완료되었습니다.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            return buildErrorResponse(e.getMessage(), "INVALID_STATE_TRANSITION", HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            return buildErrorResponse("이동 완료 처리 중 서버 내부 오류가 발생했습니다.", "SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelTransfer(
            @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        try {
            String reason = body != null ? body.get("reason") : null;
            String type = body != null ? body.get("type") : "취소";
            WarehouseTransferResponseDto data;
            if (reason != null && !reason.trim().isEmpty()) {
                data = warehouseTransferService.cancelTransfer(id, reason, type);
            } else {
                data = warehouseTransferService.cancelTransfer(id);
            }
            String msg = "반려".equals(type) ? "창고 이동 요청이 반려되었습니다." : "창고 이동 요청이 취소되었습니다.";
            return buildSuccessResponse(data, msg);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return buildErrorResponse(e.getMessage(), "INVALID_STATE_TRANSITION", HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            return buildErrorResponse("처리 중 서버 내부 오류가 발생했습니다.", "SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private ResponseEntity<Map<String, Object>> buildSuccessResponse(Object data, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("data", data);
        response.put("message", message);
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(String message, String code, HttpStatus status) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", message);
        response.put("code", code);
        return ResponseEntity.status(status).body(response);
    }
}
