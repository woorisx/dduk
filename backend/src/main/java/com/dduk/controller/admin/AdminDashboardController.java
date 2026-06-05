package com.dduk.controller.admin;

import com.dduk.dto.admin.AdminDashboardResponseDto;
import com.dduk.dto.common.ApiResponse;
import com.dduk.service.admin.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping
    public ApiResponse<AdminDashboardResponseDto> getDashboard() {
        return ApiResponse.success(adminDashboardService.getDashboard(), "관리자 대시보드 요약을 조회했습니다.");
    }
}
