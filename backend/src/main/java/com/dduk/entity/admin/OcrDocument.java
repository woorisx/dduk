package com.dduk.entity.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "ocr_documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OcrDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "stored_filename", nullable = false, unique = true, length = 255)
    private String storedFilename;

    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private OcrDocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 30)
    private OcrProcessingStatus processingStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 30)
    private OcrReviewStatus reviewStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_status", length = 30)
    private OcrLinkStatus linkStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "linked_domain_type", length = 30)
    private OcrLinkedDomainType linkedDomainType;

    @Column(name = "linked_domain_id")
    private Long linkedDomainId;

    @Column(name = "extracted_vendor", length = 100)
    private String extractedVendor;

    @Column(name = "extracted_date")
    private LocalDate extractedDate;

    @Column(name = "extracted_amount", precision = 15, scale = 2)
    private BigDecimal extractedAmount;

    @Lob
    @Column(name = "raw_ocr_result", columnDefinition = "LONGTEXT")
    private String rawOcrResult;

    @Lob
    @Column(name = "reviewed_result", columnDefinition = "LONGTEXT")
    private String reviewedResult;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_by_member_id")
    private Long createdByMemberId;

    @Column(name = "reviewed_by_member_id")
    private Long reviewedByMemberId;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "linked_by_member_id")
    private Long linkedByMemberId;

    @Column(name = "linked_at")
    private LocalDateTime linkedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public OcrDocument(
            String originalFilename,
            String storedFilename,
            String storagePath,
            String mimeType,
            long fileSize,
            OcrDocumentType documentType,
            OcrProcessingStatus processingStatus,
            OcrReviewStatus reviewStatus,
            Long createdByMemberId
    ) {
        this.originalFilename = originalFilename;
        this.storedFilename = storedFilename;
        this.storagePath = storagePath;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
        this.documentType = documentType;
        this.processingStatus = processingStatus;
        this.reviewStatus = reviewStatus;
        this.linkStatus = OcrLinkStatus.UNLINKED;
        this.createdByMemberId = createdByMemberId;
    }

    public void markProcessing() {
        this.processingStatus = OcrProcessingStatus.PROCESSING;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void markParsed(String rawOcrResult, String extractedVendor, LocalDate extractedDate, BigDecimal extractedAmount) {
        this.processingStatus = OcrProcessingStatus.PARSED;
        this.rawOcrResult = rawOcrResult;
        this.extractedVendor = extractedVendor;
        this.extractedDate = extractedDate;
        this.extractedAmount = extractedAmount;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void markFailed(String errorCode, String errorMessage, String rawOcrResult) {
        this.processingStatus = OcrProcessingStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.rawOcrResult = rawOcrResult;
    }

    public void updateReview(OcrReviewStatus reviewStatus, String reviewedResult, Long reviewerMemberId) {
        this.reviewStatus = reviewStatus;
        this.reviewedResult = reviewedResult;
        this.reviewedByMemberId = reviewerMemberId;
        this.reviewedAt = LocalDateTime.now();
    }

    public OcrLinkStatus getResolvedLinkStatus() {
        return linkStatus == null ? OcrLinkStatus.UNLINKED : linkStatus;
    }

    public boolean isLinked() {
        return getResolvedLinkStatus() == OcrLinkStatus.LINKED;
    }

    public void markLinked(OcrLinkedDomainType linkedDomainType, Long linkedDomainId, Long linkedByMemberId) {
        this.linkStatus = OcrLinkStatus.LINKED;
        this.linkedDomainType = linkedDomainType;
        this.linkedDomainId = linkedDomainId;
        this.linkedByMemberId = linkedByMemberId;
        this.linkedAt = LocalDateTime.now();
    }

    public void markUnlinked() {
        this.linkStatus = OcrLinkStatus.UNLINKED;
        this.linkedDomainType = null;
        this.linkedDomainId = null;
        this.linkedByMemberId = null;
        this.linkedAt = null;
    }

    @PrePersist
    protected void onCreate() {
        if (linkStatus == null) {
            linkStatus = OcrLinkStatus.UNLINKED;
        }
    }
}
