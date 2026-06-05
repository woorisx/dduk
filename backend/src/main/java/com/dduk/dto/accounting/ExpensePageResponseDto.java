package com.dduk.dto.accounting;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Builder
public class ExpensePageResponseDto {
    private List<ExpenseResponseDto> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public static ExpensePageResponseDto from(Page<ExpenseResponseDto> page) {
        return ExpensePageResponseDto.builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }
}
