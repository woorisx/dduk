package com.dduk.dto.admin;

import com.dduk.entity.admin.OcrReviewStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OcrDocumentReviewUpdateRequestDto {

    @NotNull(message = "검수 상태는 필수입니다.")
    private OcrReviewStatus reviewStatus;

    private String reviewedResult;
}
