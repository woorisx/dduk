package com.dduk.dto.admin;

import com.dduk.entity.admin.OcrDocument;
import com.dduk.entity.admin.OcrProcessingStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OcrDocumentUploadResponseDto {

    private Long id;
    private String originalFilename;
    private OcrProcessingStatus processingStatus;
    private String message;

    public static OcrDocumentUploadResponseDto fromEntity(OcrDocument document, String message) {
        return OcrDocumentUploadResponseDto.builder()
                .id(document.getId())
                .originalFilename(document.getOriginalFilename())
                .processingStatus(document.getProcessingStatus())
                .message(message)
                .build();
    }
}
