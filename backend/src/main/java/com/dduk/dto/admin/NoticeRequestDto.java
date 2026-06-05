package com.dduk.dto.admin;

import com.dduk.entity.admin.NoticeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoticeRequestDto {
    @NotNull
    private NoticeType type;

    @NotBlank
    @Size(max = 200)
    private String title;

    @NotBlank
    private String content;

    private LocalDate startDate;
    private LocalDate endDate;
}
