package com.dduk.controller.admin.rpa;

import com.dduk.dto.common.ApiResponse;
import com.dduk.service.admin.rpa.RpaClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/rpa")
public class AdminRpaController {

    private final RpaClientService rpaClientService;

    @PostMapping("/trigger")
    public ResponseEntity<ApiResponse<?>> triggerRpa(@RequestBody(required = false) Map<String, Object> requestBody) {
        try {
            String taskType = requestBody == null ? null : String.valueOf(requestBody.getOrDefault("taskType", ""));
            return ResponseEntity.ok(ApiResponse.success(
                    rpaClientService.triggerRpaTask(taskType),
                    "RPA trigger를 접수했어."
            ));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(exception.getMessage(), "INVALID_RPA_TASK_TYPE"));
        }
    }
}
