package com.dduk.entity.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "task_history")
public class TaskHistory {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false, unique = true, length = 100)
    private String taskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 30)
    private TaskHistoryType taskType;

    @Column(name = "action_name", nullable = false, length = 100)
    private String actionName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TaskHistoryStatus status;

    @Lob
    @Column(name = "request_payload", columnDefinition = "LONGTEXT")
    private String requestPayload;

    @Lob
    @Column(name = "response_payload", columnDefinition = "LONGTEXT")
    private String responsePayload;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "callback_received_at")
    private LocalDateTime callbackReceivedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public TaskHistory(
            String taskId,
            TaskHistoryType taskType,
            String actionName,
            TaskHistoryStatus status,
            String requestPayload,
            String responsePayload,
            String errorMessage,
            LocalDateTime requestedAt,
            LocalDateTime startedAt,
            LocalDateTime callbackReceivedAt,
            LocalDateTime completedAt
    ) {
        this.taskId = taskId;
        this.taskType = taskType;
        this.actionName = actionName;
        this.status = status;
        this.requestPayload = requestPayload;
        this.responsePayload = responsePayload;
        this.errorMessage = errorMessage;
        this.requestedAt = requestedAt;
        this.startedAt = startedAt;
        this.callbackReceivedAt = callbackReceivedAt;
        this.completedAt = completedAt;
    }

    public void markRunning() {
        this.status = TaskHistoryStatus.RUNNING;
        if (this.startedAt == null) {
            this.startedAt = LocalDateTime.now();
        }
    }

    public void markSuccess(String responsePayload) {
        this.status = TaskHistoryStatus.SUCCESS;
        this.responsePayload = responsePayload;
        this.errorMessage = null;
        this.callbackReceivedAt = LocalDateTime.now();
        this.completedAt = LocalDateTime.now();
        if (this.startedAt == null) {
            this.startedAt = this.callbackReceivedAt;
        }
    }

    public void markFailure(String errorMessage, String responsePayload) {
        this.status = TaskHistoryStatus.FAILED;
        this.errorMessage = abbreviate(errorMessage, MAX_ERROR_MESSAGE_LENGTH);
        this.responsePayload = responsePayload;
        this.callbackReceivedAt = LocalDateTime.now();
        this.completedAt = LocalDateTime.now();
        if (this.startedAt == null) {
            this.startedAt = this.callbackReceivedAt;
        }
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
