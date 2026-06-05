package com.dduk.dto.admin;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Builder
public class AdminMemberPageResponseDto {
    private List<MemberResponseDto> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public static AdminMemberPageResponseDto from(Page<MemberResponseDto> page) {
        return AdminMemberPageResponseDto.builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }
}
