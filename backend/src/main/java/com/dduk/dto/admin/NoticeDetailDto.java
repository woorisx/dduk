package com.dduk.dto.admin;

import com.dduk.entity.admin.Notice;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoticeDetailDto {
    private Long id;
    private String type;
    private String title;
    private String content;
    private LocalDate startDate;
    private LocalDate endDate;
    private int viewCount;
    private String authorId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static NoticeDetailDto fromEntity(Notice notice) {
        return NoticeDetailDto.builder()
                .id(notice.getId())
                .type(notice.getType().name())
                .title(notice.getTitle())
                .content(notice.getContent())
                .startDate(notice.getStartDate())
                .endDate(notice.getEndDate())
                .viewCount(notice.getViewCount())
                .authorId(notice.getAuthorId())
                .createdAt(notice.getCreatedAt())
                .updatedAt(notice.getUpdatedAt())
                .build();
    }
}
