package com.dduk.dto.admin;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Builder
public class AdminAuditLogPageResponseDto {
    private List<AuditLogResponseDto> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public static AdminAuditLogPageResponseDto from(Page<AuditLogResponseDto> page) {
        return AdminAuditLogPageResponseDto.builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }
}
