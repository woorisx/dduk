package com.dduk.dto.admin;

import com.dduk.entity.admin.TaskHistory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskHistoryDetailDto {
    private Long id;
    private String taskId;
    private String taskType;
    private String actionName;
    private String status;
    private String requestPayload;
    private String responsePayload;
    private String errorMessage;
    private LocalDateTime requestedAt;
    private LocalDateTime startedAt;
    private LocalDateTime callbackReceivedAt;
    private LocalDateTime completedAt;

    public static TaskHistoryDetailDto fromEntity(TaskHistory entity) {
        return TaskHistoryDetailDto.builder()
                .id(entity.getId())
                .taskId(entity.getTaskId())
                .taskType(entity.getTaskType().name())
                .actionName(entity.getActionName())
                .status(entity.getStatus().name())
                .requestPayload(entity.getRequestPayload())
                .responsePayload(entity.getResponsePayload())
                .errorMessage(entity.getErrorMessage())
                .requestedAt(entity.getRequestedAt())
                .startedAt(entity.getStartedAt())
                .callbackReceivedAt(entity.getCallbackReceivedAt())
                .completedAt(entity.getCompletedAt())
                .build();
    }
}
