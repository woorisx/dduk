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
public class OcrDocumentDetailDto {

    private Long id;
    private String originalFilename;
    private String mimeType;
    private long fileSize;
    private OcrDocumentType documentType;
    private OcrProcessingStatus processingStatus;
    private OcrReviewStatus reviewStatus;
    private OcrLinkStatus linkStatus;
    private OcrLinkedDomainType linkedDomainType;
    private Long linkedDomainId;
    private String extractedVendor;
    private LocalDate extractedDate;
    private BigDecimal extractedAmount;
    private String rawOcrResult;
    private String reviewedResult;
    private String errorCode;
    private String errorMessage;
    private Long createdByMemberId;
    private Long reviewedByMemberId;
    private LocalDateTime reviewedAt;
    private Long linkedByMemberId;
    private LocalDateTime linkedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String fileEndpoint;

    public static OcrDocumentDetailDto fromEntity(OcrDocument document, String fileEndpoint) {
        return OcrDocumentDetailDto.builder()
                .id(document.getId())
                .originalFilename(document.getOriginalFilename())
                .mimeType(document.getMimeType())
                .fileSize(document.getFileSize())
                .documentType(document.getDocumentType())
                .processingStatus(document.getProcessingStatus())
                .reviewStatus(document.getReviewStatus())
                .linkStatus(document.getResolvedLinkStatus())
                .linkedDomainType(document.getLinkedDomainType())
                .linkedDomainId(document.getLinkedDomainId())
                .extractedVendor(document.getExtractedVendor())
                .extractedDate(document.getExtractedDate())
                .extractedAmount(document.getExtractedAmount())
                .rawOcrResult(document.getRawOcrResult())
                .reviewedResult(document.getReviewedResult())
                .errorCode(document.getErrorCode())
                .errorMessage(document.getErrorMessage())
                .createdByMemberId(document.getCreatedByMemberId())
                .reviewedByMemberId(document.getReviewedByMemberId())
                .reviewedAt(document.getReviewedAt())
                .linkedByMemberId(document.getLinkedByMemberId())
                .linkedAt(document.getLinkedAt())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .fileEndpoint(fileEndpoint)
                .build();
    }
}
