package com.dduk.controller.admin.ocr;

import com.dduk.config.PrincipalDetails;
import com.dduk.dto.accounting.ExpenseResponseDto;
import com.dduk.dto.accounting.voucher.VoucherResponse;
import com.dduk.dto.admin.OcrDocumentDetailDto;
import com.dduk.dto.admin.OcrDocumentListDto;
import com.dduk.dto.admin.OcrDocumentReviewUpdateRequestDto;
import com.dduk.dto.admin.OcrDocumentUploadResponseDto;
import com.dduk.dto.admin.OcrExpenseLinkRequestDto;
import com.dduk.dto.admin.OcrPurchaseOrderLinkRequestDto;
import com.dduk.dto.admin.OcrVoucherLinkRequestDto;
import com.dduk.dto.common.ApiResponse;
import com.dduk.dto.inventory.PurchaseOrderResponseDto;
import com.dduk.entity.admin.OcrDocumentType;
import com.dduk.entity.admin.OcrLinkStatus;
import com.dduk.entity.admin.OcrProcessingStatus;
import com.dduk.entity.admin.OcrReviewStatus;
import com.dduk.service.admin.ocr.OcrDocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
public class OcrDocumentController {

    private final OcrDocumentService ocrDocumentService;

    @PostMapping("/api/v1/ai/ocr/documents")
    public ApiResponse<OcrDocumentUploadResponseDto> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "RECEIPT") OcrDocumentType documentType,
            @AuthenticationPrincipal PrincipalDetails principalDetails
    ) {
        Long memberId = principalDetails != null ? principalDetails.getMember().getId() : null;
        return ApiResponse.success(
                ocrDocumentService.uploadAndParse(file, documentType, memberId),
                "OCR 문서를 업로드하고 분석했어."
        );
    }

    @GetMapping("/api/v1/admin/ocr-documents")
    public ApiResponse<Page<OcrDocumentListDto>> getDocuments(
            @RequestParam(required = false) OcrProcessingStatus processingStatus,
            @RequestParam(required = false) OcrDocumentType documentType,
            @RequestParam(required = false) OcrReviewStatus reviewStatus,
            @RequestParam(required = false) OcrLinkStatus linkStatus,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 12, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.success(
                ocrDocumentService.getDocuments(processingStatus, documentType, reviewStatus, linkStatus, keyword, pageable),
                "OCR 문서 목록을 조회했어."
        );
    }

    @GetMapping("/api/v1/admin/ocr-documents/{id}")
    public ApiResponse<OcrDocumentDetailDto> getDocumentDetail(@PathVariable Long id) {
        return ApiResponse.success(
                ocrDocumentService.getDocumentDetail(id),
                "OCR 문서 상세를 조회했어."
        );
    }

    @PatchMapping("/api/v1/admin/ocr-documents/{id}/review")
    public ApiResponse<OcrDocumentDetailDto> updateReview(
            @PathVariable Long id,
            @Valid @RequestBody OcrDocumentReviewUpdateRequestDto requestDto,
            @AuthenticationPrincipal PrincipalDetails principalDetails
    ) {
        Long reviewerMemberId = principalDetails != null ? principalDetails.getMember().getId() : null;
        return ApiResponse.success(
                ocrDocumentService.updateReview(id, requestDto, reviewerMemberId),
                "OCR 문서 검수 상태를 변경했어."
        );
    }

    @PostMapping("/api/v1/admin/ocr-documents/{id}/retry")
    public ApiResponse<OcrDocumentDetailDto> retryDocument(@PathVariable Long id) {
        return ApiResponse.success(
                ocrDocumentService.retryDocument(id),
                "OCR 문서를 다시 분석했어."
        );
    }

    @DeleteMapping("/api/v1/admin/ocr-documents/{id}/link")
    public ApiResponse<OcrDocumentDetailDto> unlinkDocument(@PathVariable Long id) {
        return ApiResponse.success(
                ocrDocumentService.unlinkDocument(id),
                "OCR document unlinked."
        );
    }

    @PostMapping("/api/v1/admin/ocr-documents/{id}/link/purchase-orders")
    public ApiResponse<PurchaseOrderResponseDto> linkPurchaseOrder(
            @PathVariable Long id,
            @RequestBody OcrPurchaseOrderLinkRequestDto requestDto,
            @AuthenticationPrincipal PrincipalDetails principalDetails
    ) {
        Long actorMemberId = principalDetails != null ? principalDetails.getMember().getId() : null;
        return ApiResponse.success(
                ocrDocumentService.linkPurchaseOrder(id, requestDto, actorMemberId),
                "OCR 문서를 구매 발주와 연결했어."
        );
    }

    @PostMapping("/api/v1/admin/ocr-documents/{id}/link/expenses")
    public ApiResponse<ExpenseResponseDto> linkExpense(
            @PathVariable Long id,
            @RequestBody OcrExpenseLinkRequestDto requestDto,
            @AuthenticationPrincipal PrincipalDetails principalDetails
    ) {
        Long actorMemberId = principalDetails != null ? principalDetails.getMember().getId() : null;
        return ApiResponse.success(
                ocrDocumentService.linkExpense(id, requestDto, actorMemberId),
                "OCR 문서를 비용 증빙과 연결했어."
        );
    }

    @PostMapping("/api/v1/admin/ocr-documents/{id}/link/vouchers")
    public ApiResponse<VoucherResponse> linkVoucher(
            @PathVariable Long id,
            @RequestBody OcrVoucherLinkRequestDto requestDto,
            @AuthenticationPrincipal PrincipalDetails principalDetails
    ) {
        Long actorMemberId = principalDetails != null ? principalDetails.getMember().getId() : null;
        return ApiResponse.success(
                ocrDocumentService.linkVoucher(id, requestDto, actorMemberId),
                "OCR 문서를 전표와 연결했어."
        );
    }

    @GetMapping("/api/v1/admin/ocr-documents/{id}/file")
    public ResponseEntity<Resource> getDocumentFile(@PathVariable Long id) {
        Resource resource = ocrDocumentService.getDocumentFile(id);
        String mimeType = ocrDocumentService.getDocumentMimeType(id);
        String originalFilename = ocrDocumentService.getDocumentOriginalFilename(id);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(originalFilename, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(resource);
    }

    @DeleteMapping("/api/v1/admin/ocr-documents/{id}")
    public ApiResponse<Void> deleteDocument(@PathVariable Long id) {
        ocrDocumentService.deleteDocument(id);
        return ApiResponse.success(null, "OCR 문서를 삭제했어.");
    }
}
