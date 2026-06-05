package com.dduk.controller.inventory;

import com.dduk.service.inventory.VendorService;
import com.dduk.dto.inventory.VendorCreateDto;
import com.dduk.dto.inventory.VendorResponseDto;
import com.dduk.dto.inventory.VendorStatusUpdateDto;
import com.dduk.dto.inventory.VendorUpdateDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory/vendors")
@RequiredArgsConstructor
public class VendorController {

    private final VendorService vendorService;

    @GetMapping
    public List<VendorResponseDto> getVendors() {
        return vendorService.getVendors();
    }

    @GetMapping("/search")
    public List<VendorResponseDto> searchVendors(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String representativeName,
            @RequestParam(required = false) String contactPhone,
            @RequestParam(required = false) String businessRegistrationNo
    ) {
        if (keyword != null) {
            return vendorService.searchVendorsByKeyword(keyword);
        }
        return vendorService.searchVendors(name, representativeName, contactPhone, businessRegistrationNo);
    }

    @GetMapping("/{vendorId}")
    public VendorResponseDto getVendor(@PathVariable Long vendorId) {
        return vendorService.getVendor(vendorId);
    }

    @PostMapping
    public VendorResponseDto createVendor(@RequestBody VendorCreateDto requestDto) {
        return vendorService.createVendor(requestDto);
    }

    @PatchMapping("/{vendorId}")
    public VendorResponseDto updateVendor(@PathVariable Long vendorId, @RequestBody VendorUpdateDto requestDto) {
        return vendorService.updateVendor(vendorId, requestDto);
    }

    @PatchMapping("/{vendorId}/status")
    public VendorResponseDto updateVendorStatus(
            @PathVariable Long vendorId,
            @RequestBody VendorStatusUpdateDto requestDto
    ) {
        return vendorService.updateVendorStatus(vendorId, requestDto);
    }
}
