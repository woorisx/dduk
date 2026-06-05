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
public class NoticeListDto {
    private Long id;
    private String type;
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private int viewCount;
    private LocalDateTime createdAt;

    public static NoticeListDto fromEntity(Notice notice) {
        return NoticeListDto.builder()
                .id(notice.getId())
                .type(notice.getType().name())
                .title(notice.getTitle())
                .startDate(notice.getStartDate())
                .endDate(notice.getEndDate())
                .viewCount(notice.getViewCount())
                .createdAt(notice.getCreatedAt())
                .build();
    }
}
