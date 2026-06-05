package com.dduk.dto.admin;

import com.dduk.entity.admin.OcrDocument;
import com.dduk.entity.admin.OcrDocumentType;
import com.dduk.entity.admin.OcrLinkStatus;
import com.dduk.entity.admin.OcrLinkedDomainType;
import com.dduk.entity.admin.OcrProcessingStatus;
import com.dduk.entity.admin.OcrReviewStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class OcrDocumentListDto {

    private Long id;
    private String originalFilename;
    private OcrDocumentType documentType;
    private OcrProcessingStatus processingStatus;
    private OcrReviewStatus reviewStatus;
    private OcrLinkStatus linkStatus;
    private OcrLinkedDomainType linkedDomainType;
    private Long linkedDomainId;
    private String extractedVendor;
    private LocalDate extractedDate;
    private BigDecimal extractedAmount;
    private long fileSize;
    private LocalDateTime createdAt;

    public static OcrDocumentListDto fromEntity(OcrDocument document) {
        return OcrDocumentListDto.builder()
                .id(document.getId())
                .originalFilename(document.getOriginalFilename())
                .documentType(document.getDocumentType())
                .processingStatus(document.getProcessingStatus())
                .reviewStatus(document.getReviewStatus())
                .linkStatus(document.getResolvedLinkStatus())
                .linkedDomainType(document.getLinkedDomainType())
                .linkedDomainId(document.getLinkedDomainId())
                .extractedVendor(document.getExtractedVendor())
                .extractedDate(document.getExtractedDate())
                .extractedAmount(document.getExtractedAmount())
                .fileSize(document.getFileSize())
                .createdAt(document.getCreatedAt())
                .build();
    }
}
