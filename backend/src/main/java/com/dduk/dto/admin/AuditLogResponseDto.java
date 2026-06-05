package com.dduk.dto.admin;

import com.dduk.entity.admin.AuditLog;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AuditLogResponseDto {
    private Long id;
    private String action;
    private Long actorMemberId;
    private String actorLoginId;
    private String actorName;
    private String targetType;
    private Long targetId;
    private String targetLabel;
    private String details;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime createdAt;

    public static AuditLogResponseDto from(AuditLog auditLog) {
        return AuditLogResponseDto.builder()
                .id(auditLog.getId())
                .action(auditLog.getAction().name())
                .actorMemberId(auditLog.getActorMember() != null ? auditLog.getActorMember().getId() : null)
                .actorLoginId(auditLog.getActorMember() != null ? auditLog.getActorMember().getLoginId() : null)
                .actorName(auditLog.getActorMember() != null ? auditLog.getActorMember().getName() : null)
                .targetType(auditLog.getTargetType())
                .targetId(auditLog.getTargetId())
                .targetLabel(auditLog.getTargetLabel())
                .details(auditLog.getDetails())
                .ipAddress(auditLog.getIpAddress())
                .userAgent(auditLog.getUserAgent())
                .createdAt(auditLog.getCreatedAt())
                .build();
    }
}
