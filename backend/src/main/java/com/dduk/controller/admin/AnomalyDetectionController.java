package com.dduk.controller.admin;

import com.dduk.dto.admin.AnomalyDetectionSummaryDto;
import com.dduk.dto.admin.AnomalyLogListDto;
import com.dduk.dto.admin.AnomalyLogStatusUpdateDto;
import com.dduk.dto.admin.AnomalyRefreshResponseDto;
import com.dduk.dto.common.ApiResponse;
import com.dduk.entity.admin.AnomalyLog;
import com.dduk.service.admin.AnomalyDetectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/anomaly-logs")
@RequiredArgsConstructor
public class AnomalyDetectionController {

    private final AnomalyDetectionService anomalyDetectionService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<AnomalyLogListDto>>> getLogs(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String ruleCode,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "lastDetectedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<AnomalyLogListDto> page = anomalyDetectionService
                .getAnomalyLogs(status, active, severity, ruleCode, keyword, pageable)
                .map(AnomalyLogListDto::fromEntity);
        return ResponseEntity.ok(ApiResponse.success(page, "이상 탐지 경고 목록을 조회했어."));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AnomalyDetectionSummaryDto>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success(anomalyDetectionService.getSummary(), "이상 탐지 요약을 조회했어."));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AnomalyRefreshResponseDto>> refresh() {
        return ResponseEntity.ok(ApiResponse.success(anomalyDetectionService.refreshAnomalies(), "규칙 기반 이상 탐지를 다시 실행했어."));
    }

    @PatchMapping("/{anomalyId}/status")
    public ResponseEntity<ApiResponse<AnomalyLogListDto>> updateStatus(
            @PathVariable Long anomalyId,
            @Valid @RequestBody AnomalyLogStatusUpdateDto request
    ) {
        AnomalyLog updated = anomalyDetectionService.updateStatus(anomalyId, request);
        return ResponseEntity.ok(ApiResponse.success(AnomalyLogListDto.fromEntity(updated), "이상 탐지 상태를 업데이트했어."));
    }
}
