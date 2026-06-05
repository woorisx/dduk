package com.dduk.service.admin.rpa;

import com.dduk.service.admin.taskhistory.TaskHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class RpaClientService {

    private static final String TASK_TYPE_PURCHASE_PRICE = "PURCHASE_PRICE";
    private static final String TASK_TYPE_INVENTORY_SHORTAGE = "INVENTORY_SHORTAGE";
    private static final String TASK_TYPE_HR_MIN_WAGE = "HR_MIN_WAGE";

    private static final String ACTION_PURCHASE_PRICE = "collect_purchase_orders";
    private static final String ACTION_INVENTORY_SHORTAGE = "check_inventory_shortage";
    private static final String ACTION_HR_MIN_WAGE = "collect_hr_reference";

    private final RestTemplate restTemplate;
    private final TaskHistoryService taskHistoryService;
    private final com.dduk.service.inventory.InventoryAgingAnalysisService inventoryAgingAnalysisService;

    @Value("${rpa-server.url:http://localhost:5050}")
    private String rpaServerUrl;

    public RpaClientService(RestTemplateBuilder restTemplateBuilder, TaskHistoryService taskHistoryService, com.dduk.service.inventory.InventoryAgingAnalysisService inventoryAgingAnalysisService) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(3))
                .build();
        this.taskHistoryService = taskHistoryService;
        this.inventoryAgingAnalysisService = inventoryAgingAnalysisService;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> triggerRpaTask(String taskType) {
        String normalizedTaskType = normalizeTaskType(taskType);
        String actionName = resolveActionName(normalizedTaskType);
        String taskId = "rpa-task-" + UUID.randomUUID().toString().substring(0, 8);
        String endpoint = rpaServerUrl + "/api/v1/rpa/trigger";
        log.info("[RPA Client] Triggering async RPA task: {} ({}) to endpoint: {}", taskId, normalizedTaskType, endpoint);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("taskId", taskId);
        requestBody.put("taskType", normalizedTaskType);
        requestBody.put("action", actionName);

        taskHistoryService.createRpaTriggerRequest(taskId, actionName, requestBody);

        if (TASK_TYPE_INVENTORY_SHORTAGE.equals(normalizedTaskType)) {
            taskHistoryService.markRpaTaskAccepted(taskId);
            inventoryAgingAnalysisService.runAsync(taskId, normalizedTaskType, actionName);
            log.info("[RPA Client] Internal Inventory Aging analysis triggered. Task ID: {}, action: {}", taskId, actionName);
            return Map.of(
                    "taskId", taskId,
                    "taskType", normalizedTaskType,
                    "actionName", actionName,
                    "accepted", true
            );
        }

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> responseEntity = restTemplate.postForEntity(endpoint, requestEntity, Map.class);

            if (responseEntity.getStatusCode() == HttpStatus.ACCEPTED || responseEntity.getStatusCode() == HttpStatus.OK) {
                taskHistoryService.markRpaTaskAccepted(taskId);
                log.info("[RPA Client] RPA trigger accepted. Task ID: {}, action: {}", taskId, actionName);
                return Map.of(
                        "taskId", taskId,
                        "taskType", normalizedTaskType,
                        "actionName", actionName,
                        "accepted", true
                );
            }

            taskHistoryService.markRpaTriggerFailure(
                    taskId,
                    actionName,
                    "Unexpected RPA trigger response status: " + responseEntity.getStatusCode(),
                    responseEntity.getBody()
            );
            log.warn("[RPA Client] Unexpected trigger response for task {}: {}", taskId, responseEntity.getStatusCode());
            return Map.of(
                    "taskId", taskId,
                    "taskType", normalizedTaskType,
                    "actionName", actionName,
                    "accepted", false
            );
        } catch (ResourceAccessException exception) {
            taskHistoryService.markRpaTriggerFailure(
                    taskId,
                    actionName,
                    "RPA server connection timeout",
                    Map.of("endpoint", endpoint)
            );
            log.error("[RPA Client] Connection timeout calling RPA server trigger: {}", exception.getMessage());
            throw new RuntimeException("RPA 서비스 연결이 지연되고 있어. 관리자에게 문의해.", exception);
        } catch (Exception exception) {
            taskHistoryService.markRpaTriggerFailure(
                    taskId,
                    actionName,
                    "RPA trigger failed",
                    Map.of("endpoint", endpoint)
            );
            log.error("[RPA Client] Failed to trigger RPA task", exception);
            throw new RuntimeException("RPA 연동 기동에 실패했어.", exception);
        }
    }

    private String normalizeTaskType(String taskType) {
        if (taskType == null || taskType.isBlank()) {
            return TASK_TYPE_PURCHASE_PRICE;
        }

        String normalized = taskType.trim().toUpperCase();
        return switch (normalized) {
            case TASK_TYPE_PURCHASE_PRICE, TASK_TYPE_INVENTORY_SHORTAGE, TASK_TYPE_HR_MIN_WAGE -> normalized;
            default -> throw new IllegalArgumentException("지원하지 않는 RPA taskType이야: " + taskType);
        };
    }

    private String resolveActionName(String taskType) {
        return switch (taskType) {
            case TASK_TYPE_PURCHASE_PRICE -> ACTION_PURCHASE_PRICE;
            case TASK_TYPE_INVENTORY_SHORTAGE -> ACTION_INVENTORY_SHORTAGE;
            case TASK_TYPE_HR_MIN_WAGE -> ACTION_HR_MIN_WAGE;
            default -> throw new IllegalArgumentException("지원하지 않는 RPA taskType이야: " + taskType);
        };
    }
}
