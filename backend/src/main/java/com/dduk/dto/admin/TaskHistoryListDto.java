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
public class TaskHistoryListDto {
    private Long id;
    private String taskId;
    private String taskType;
    private String actionName;
    private String status;
    private String errorMessage;
    private LocalDateTime requestedAt;
    private LocalDateTime completedAt;

    public static TaskHistoryListDto fromEntity(TaskHistory entity) {
        return TaskHistoryListDto.builder()
                .id(entity.getId())
                .taskId(entity.getTaskId())
                .taskType(entity.getTaskType().name())
                .actionName(entity.getActionName())
                .status(entity.getStatus().name())
                .errorMessage(entity.getErrorMessage())
                .requestedAt(entity.getRequestedAt())
                .completedAt(entity.getCompletedAt())
                .build();
    }
}
