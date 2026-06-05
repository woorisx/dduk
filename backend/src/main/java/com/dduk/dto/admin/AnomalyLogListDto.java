package com.dduk.dto.admin;

import com.dduk.entity.admin.AnomalyLog;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AnomalyLogListDto {
    private Long id;
    private String anomalyKey;
    private String ruleCode;
    private String severity;
    private String title;
    private String summary;
    private String sourceType;
    private String sourceId;
    private String sourceLabel;
    private String status;
    private boolean active;
    private String firstDetectedAt;
    private String lastDetectedAt;
    private String reviewedAt;
    private String reviewedBy;
    private String reviewNote;

    public static AnomalyLogListDto fromEntity(AnomalyLog entity) {
        return AnomalyLogListDto.builder()
                .id(entity.getId())
                .anomalyKey(entity.getAnomalyKey())
                .ruleCode(entity.getRuleCode())
                .severity(entity.getSeverity())
                .title(entity.getTitle())
                .summary(entity.getSummary())
                .sourceType(entity.getSourceType())
                .sourceId(entity.getSourceId())
                .sourceLabel(entity.getSourceLabel())
                .status(entity.getStatus().name())
                .active(entity.isActive())
                .firstDetectedAt(entity.getFirstDetectedAt() == null ? null : entity.getFirstDetectedAt().toString())
                .lastDetectedAt(entity.getLastDetectedAt() == null ? null : entity.getLastDetectedAt().toString())
                .reviewedAt(entity.getReviewedAt() == null ? null : entity.getReviewedAt().toString())
                .reviewedBy(entity.getReviewedBy())
                .reviewNote(entity.getReviewNote())
                .build();
    }
}
