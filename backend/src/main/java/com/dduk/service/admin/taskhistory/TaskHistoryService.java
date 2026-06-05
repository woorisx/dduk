package com.dduk.service.admin.taskhistory;

import com.dduk.entity.admin.TaskHistory;
import com.dduk.entity.admin.TaskHistoryStatus;
import com.dduk.entity.admin.TaskHistoryType;
import com.dduk.repository.admin.TaskHistoryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.EnumSet;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TaskHistoryService {

    private static final String DEFAULT_RPA_ACTION = "collect_purchase_orders";
    private static final String DEFAULT_AI_ACTION = "ai_chat_request";

    private final TaskHistoryRepository taskHistoryRepository;
    private final ObjectMapper objectMapper;

    public void createRpaTriggerRequest(String taskId, String actionName, Object requestPayload) {
        taskHistoryRepository.findByTaskId(taskId).ifPresentOrElse(existingTask -> {
            existingTask.markRunning();
        }, () -> taskHistoryRepository.save(TaskHistory.builder()
                .taskId(taskId)
                .taskType(TaskHistoryType.RPA)
                .actionName(resolveActionName(actionName))
                .status(TaskHistoryStatus.REQUESTED)
                .requestPayload(toJson(requestPayload))
                .requestedAt(LocalDateTime.now())
                .build()));
    }

    public void markRpaTaskAccepted(String taskId) {
        taskHistoryRepository.findByTaskId(taskId).ifPresent(TaskHistory::markRunning);
    }

    public void markRpaTriggerFailure(String taskId, String actionName, String errorMessage, Object responsePayload) {
        TaskHistory taskHistory = taskHistoryRepository.findByTaskId(taskId)
                .orElseGet(() -> TaskHistory.builder()
                        .taskId(taskId)
                        .taskType(TaskHistoryType.RPA)
                        .actionName(resolveActionName(actionName))
                        .status(TaskHistoryStatus.REQUESTED)
                        .requestedAt(LocalDateTime.now())
                        .build());

        taskHistory.markFailure(errorMessage, toJson(responsePayload));
        taskHistoryRepository.save(taskHistory);
    }

    public void saveRpaCallbackResult(String taskId, String actionName, String status, Object payload, Object errorPayload) {
        TaskHistory taskHistory = taskHistoryRepository.findByTaskId(taskId)
                .orElseGet(() -> TaskHistory.builder()
                        .taskId(taskId)
                        .taskType(TaskHistoryType.RPA)
                        .actionName(resolveActionName(actionName))
                        .status(TaskHistoryStatus.REQUESTED)
                        .requestedAt(LocalDateTime.now())
                        .build());

        if ("success".equalsIgnoreCase(status)) {
            taskHistory.markSuccess(toJson(payload));
        } else {
            taskHistory.markFailure(extractErrorMessage(errorPayload), toJson(payload));
        }

        taskHistoryRepository.save(taskHistory);
    }

    @Transactional(readOnly = true)
    public long countTasksByType(TaskHistoryType taskType) {
        return taskHistoryRepository.countByTaskType(taskType);
    }

    @Transactional(readOnly = true)
    public long countTasksByTypeAndStatus(TaskHistoryType taskType, TaskHistoryStatus status) {
        return taskHistoryRepository.countByTaskTypeAndStatus(taskType, status);
    }

    @Transactional(readOnly = true)
    public long countActiveTasksByType(TaskHistoryType taskType) {
        return taskHistoryRepository.countByTaskTypeAndStatusIn(taskType, EnumSet.of(TaskHistoryStatus.REQUESTED, TaskHistoryStatus.RUNNING));
    }

    @Transactional(readOnly = true)
    public long countRecentFailures(LocalDateTime threshold) {
        return taskHistoryRepository.countByStatusAndCompletedAtAfter(TaskHistoryStatus.FAILED, threshold);
    }

    @Transactional(readOnly = true)
    public String calculateSuccessRate(TaskHistoryType taskType) {
        long successCount = taskHistoryRepository.countByTaskTypeAndStatus(taskType, TaskHistoryStatus.SUCCESS);
        long failureCount = taskHistoryRepository.countByTaskTypeAndStatus(taskType, TaskHistoryStatus.FAILED);
        long completedCount = successCount + failureCount;

        if (completedCount == 0) {
            return "-";
        }

        double rate = (successCount * 100.0) / completedCount;
        return String.format("%.1f%%", rate);
    }

    private String resolveActionName(String actionName) {
        if (actionName == null || actionName.isBlank()) {
            return DEFAULT_RPA_ACTION;
        }
        return actionName;
    }

    public void createAiTask(String taskId, String actionName, Object requestPayload) {
        taskHistoryRepository.save(TaskHistory.builder()
                .taskId(taskId)
                .taskType(TaskHistoryType.AI)
                .actionName(actionName != null && !actionName.isBlank() ? actionName : DEFAULT_AI_ACTION)
                .status(TaskHistoryStatus.RUNNING)
                .requestPayload(toJson(requestPayload))
                .requestedAt(LocalDateTime.now())
                .startedAt(LocalDateTime.now())
                .build());
    }

    public void markAiSuccess(String taskId, Object responsePayload) {
        taskHistoryRepository.findByTaskId(taskId).ifPresent(task -> {
            task.markSuccess(toJson(responsePayload));
            taskHistoryRepository.save(task);
        });
    }

    public void markAiFailure(String taskId, String errorMessage, Object errorPayload) {
        taskHistoryRepository.findByTaskId(taskId).ifPresent(task -> {
            task.markFailure(errorMessage, toJson(errorPayload));
            taskHistoryRepository.save(task);
        });
    }

    @Transactional(readOnly = true)
    public Page<TaskHistory> getTaskHistoryList(Pageable pageable) {
        return taskHistoryRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<TaskHistory> getTaskHistoryList(String taskType, String actionName, Pageable pageable) {
        TaskHistoryType normalizedTaskType = normalizeTaskType(taskType);
        String normalizedActionName = normalizeActionName(actionName);
        return taskHistoryRepository.findRecentByTaskScope(normalizedTaskType, normalizedActionName, pageable);
    }

    @Transactional(readOnly = true)
    public TaskHistory getTaskHistoryDetail(String taskId) {
        return taskHistoryRepository.findByTaskId(taskId).orElse(null);
    }

    private TaskHistoryType normalizeTaskType(String taskType) {
        if (taskType == null || taskType.isBlank()) {
            return null;
        }
        try {
            return TaskHistoryType.valueOf(taskType.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 taskType입니다: " + taskType);
        }
    }

    private String normalizeActionName(String actionName) {
        if (actionName == null || actionName.isBlank()) {
            return null;
        }
        return actionName.trim();
    }

    private String extractErrorMessage(Object errorPayload) {
        if (errorPayload == null) {
            return "RPA task failed";
        }
        return String.valueOf(errorPayload);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            log.warn("[Task History] Failed to serialize payload. Falling back to string form.", exception);
            return String.valueOf(value);
        }
    }
}
