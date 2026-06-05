package com.dduk.controller.inventory;

import com.dduk.config.PrincipalDetails;
import com.dduk.service.inventory.PurchaseService;
import com.dduk.dto.inventory.PurchaseOrderCreateDto;
import com.dduk.dto.inventory.PurchaseOrderResponseDto;
import com.dduk.dto.inventory.PurchaseOrderStatusUpdateDto;
import com.dduk.dto.inventory.PurchaseOrderUpdateDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseService purchaseService;

    @GetMapping
    public List<PurchaseOrderResponseDto> getAll(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean all
    ) {
        return purchaseService.searchOrderResponses(keyword, all);
    }

    @GetMapping("/management")
    public List<PurchaseOrderResponseDto> getManagementOrders(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean all
    ) {
        return purchaseService.searchManagementOrderResponses(keyword, all);
    }

    @GetMapping("/{id}")
    public PurchaseOrderResponseDto getOne(@PathVariable Long id) {
        return purchaseService.getOrderResponse(id);
    }

    @GetMapping("/receivable")
    public List<PurchaseOrderResponseDto> getReceivableOrders(@RequestParam(required = false) String keyword) {
        return purchaseService.getReceivableOrderResponses(keyword);
    }

    @PostMapping
    public PurchaseOrderResponseDto createPurchaseOrder(
            @RequestBody PurchaseOrderCreateDto requestDto,
            @AuthenticationPrincipal PrincipalDetails principalDetails
    ) {
        Long memberId = principalDetails != null ? principalDetails.getMember().getId() : null;
        return purchaseService.createPurchaseOrder(requestDto, memberId);
    }

    @PutMapping("/{id}")
    public PurchaseOrderResponseDto updatePurchaseOrder(
            @PathVariable Long id,
            @RequestBody PurchaseOrderUpdateDto requestDto
    ) {
        return purchaseService.updatePurchaseOrder(id, requestDto);
    }

    @PatchMapping("/{id}/status")
    public PurchaseOrderResponseDto updatePurchaseOrderStatus(
            @PathVariable Long id,
            @RequestBody PurchaseOrderStatusUpdateDto requestDto,
            @AuthenticationPrincipal PrincipalDetails principalDetails
    ) {
        Long memberId = principalDetails != null ? principalDetails.getMember().getId() : null;
        return purchaseService.updatePurchaseOrderStatus(id, requestDto, memberId);
    }

    @PatchMapping("/{id}/management-status")
    public PurchaseOrderResponseDto updatePurchaseOrderStatusForManagement(
            @PathVariable Long id,
            @RequestBody PurchaseOrderStatusUpdateDto requestDto,
            @AuthenticationPrincipal PrincipalDetails principalDetails
    ) {
        requireSuperAdmin(principalDetails);
        return purchaseService.updatePurchaseOrderStatusForManagement(id, requestDto);
    }

    @PostMapping("/{id}/receive")
    public PurchaseOrderResponseDto receivePurchaseOrder(@PathVariable Long id) {
        return purchaseService.receivePurchaseOrder(id);
    }

    @PostMapping("/{id}/cancel")
    public PurchaseOrderResponseDto cancelPurchaseOrder(@PathVariable Long id) {
        return purchaseService.cancelPurchaseOrder(id);
    }

    @PostMapping("/{id}/send-to-vendor")
    public PurchaseOrderResponseDto sendPurchaseOrderToVendor(
            @PathVariable Long id,
            @AuthenticationPrincipal PrincipalDetails principalDetails
    ) {
        requireSuperAdmin(principalDetails);
        return purchaseService.sendPurchaseOrderToVendor(id);
    }

    private void requireSuperAdmin(PrincipalDetails principalDetails) {
        String role = principalDetails != null && principalDetails.getMember() != null
                ? String.valueOf(principalDetails.getMember().getRole())
                : null;
        String loginId = principalDetails != null && principalDetails.getMember() != null
                ? principalDetails.getMember().getLoginId()
                : null;
        if (!"ADMIN".equalsIgnoreCase(role) || !"admin".equalsIgnoreCase(loginId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "최고관리자(admin)만 발주 상태를 변경할 수 있습니다.");
        }
    }
}
