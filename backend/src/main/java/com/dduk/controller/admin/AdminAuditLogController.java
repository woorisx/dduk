package com.dduk.controller.admin;

import com.dduk.dto.admin.AdminAuditLogPageResponseDto;
import com.dduk.dto.common.ApiResponse;
import com.dduk.entity.admin.AuditAction;
import com.dduk.service.admin.AdminAuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AdminAuditLogService adminAuditLogService;

    @GetMapping
    public ApiResponse<AdminAuditLogPageResponseDto> getAuditLogs(
            @RequestParam(required = false) String target,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(
                adminAuditLogService.getAuditLogs(target, actor, action, dateFrom, dateTo, page, size),
                "감사 로그를 조회했습니다."
        );
    }
}
