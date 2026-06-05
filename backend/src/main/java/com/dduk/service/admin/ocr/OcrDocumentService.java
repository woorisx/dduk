package com.dduk.service.admin.ocr;

import com.dduk.dto.accounting.ExpenseResponseDto;
import com.dduk.dto.accounting.voucher.VoucherRequest;
import com.dduk.dto.accounting.voucher.VoucherResponse;
import com.dduk.dto.admin.OcrDocumentDetailDto;
import com.dduk.dto.admin.OcrDocumentListDto;
import com.dduk.dto.admin.OcrDocumentReviewUpdateRequestDto;
import com.dduk.dto.admin.OcrDocumentUploadResponseDto;
import com.dduk.dto.admin.OcrExpenseLinkRequestDto;
import com.dduk.dto.admin.OcrPurchaseOrderLinkRequestDto;
import com.dduk.dto.admin.OcrVoucherLinkRequestDto;
import com.dduk.dto.inventory.PurchaseOrderResponseDto;
import com.dduk.entity.admin.Member;
import com.dduk.entity.admin.OcrDocument;
import com.dduk.entity.admin.OcrDocumentType;
import com.dduk.entity.admin.OcrLinkStatus;
import com.dduk.entity.admin.OcrLinkedDomainType;
import com.dduk.entity.admin.OcrProcessingStatus;
import com.dduk.entity.admin.OcrReviewStatus;
import com.dduk.entity.accounting.voucher.enums.VoucherType;
import com.dduk.repository.admin.MemberRepository;
import com.dduk.repository.admin.OcrDocumentRepository;
import com.dduk.service.accounting.ExpenseService;
import com.dduk.service.accounting.voucher.VoucherService;
import com.dduk.service.admin.ai.AiClientService;
import com.dduk.service.admin.taskhistory.TaskHistoryService;
import com.dduk.service.inventory.PurchaseService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcrDocumentService {

    private static final int MAX_ORIGINAL_FILENAME_LENGTH = 255;
    private static final int MAX_EXTRACTED_VENDOR_LENGTH = 100;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/webp"
    );

    private final OcrDocumentRepository ocrDocumentRepository;
    private final MemberRepository memberRepository;
    private final AiClientService aiClientService;
    private final TaskHistoryService taskHistoryService;
    private final PurchaseService purchaseService;
    private final ExpenseService expenseService;
    private final VoucherService voucherService;
    private final ObjectMapper objectMapper;

    @Value("${ocr.upload-dir:uploads/ocr}")
    private String uploadDir;

    @Value("${ocr.max-file-size-bytes:10485760}")
    private long maxFileSizeBytes;

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public OcrDocumentUploadResponseDto uploadAndParse(MultipartFile file, OcrDocumentType documentType, Long memberId) {
        validateFile(file);

        if (memberId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증된 사용자 정보가 없어.");
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "업로드 사용자를 찾을 수 없어."));

        Path storageDirectory = resolveStorageDirectory();
        String originalFilename = normalizeOriginalFilename(file.getOriginalFilename());
        String extension = extractExtension(originalFilename);
        String storedFilename = UUID.randomUUID() + extension;
        Path storedPath = storageDirectory.resolve(storedFilename).normalize();

        try {
            Files.createDirectories(storageDirectory);
            file.transferTo(storedPath);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일 저장에 실패했어.");
        }

        OcrDocument document = ocrDocumentRepository.save(OcrDocument.builder()
                .originalFilename(originalFilename)
                .storedFilename(storedFilename)
                .storagePath(storedPath.toString())
                .mimeType(file.getContentType())
                .fileSize(file.getSize())
                .documentType(documentType)
                .processingStatus(OcrProcessingStatus.UPLOADED)
                .reviewStatus(OcrReviewStatus.PENDING)
                .createdByMemberId(member.getId())
                .build());

        try {
            processDocument(document, file.getBytes(), originalFilename, file.getContentType(), true);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "업로드 파일을 읽지 못했어.");
        }
        return OcrDocumentUploadResponseDto.fromEntity(document, "OCR 분석을 완료했어.");
    }

    @Transactional(readOnly = true)
    public Page<OcrDocumentListDto> getDocuments(
            OcrProcessingStatus processingStatus,
            OcrDocumentType documentType,
            OcrReviewStatus reviewStatus,
            OcrLinkStatus linkStatus,
            String keyword,
            Pageable pageable
    ) {
        Specification<OcrDocument> specification = Specification.where(null);

        if (processingStatus != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("processingStatus"), processingStatus));
        }

        if (documentType != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("documentType"), documentType));
        }

        if (reviewStatus != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("reviewStatus"), reviewStatus));
        }

        if (linkStatus != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("linkStatus"), linkStatus));
        }

        if (keyword != null && !keyword.isBlank()) {
            String likeKeyword = "%" + keyword.trim().toLowerCase() + "%";
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("originalFilename")), likeKeyword),
                    criteriaBuilder.like(criteriaBuilder.lower(criteriaBuilder.coalesce(root.get("extractedVendor").as(String.class), "")), likeKeyword)
            ));
        }

        return ocrDocumentRepository.findAll(specification, pageable).map(OcrDocumentListDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public OcrDocumentDetailDto getDocumentDetail(Long id) {
        OcrDocument document = getDocumentEntity(id);
        return OcrDocumentDetailDto.fromEntity(document, "/api/v1/admin/ocr-documents/" + id + "/file");
    }

    @Transactional
    public OcrDocumentDetailDto updateReview(Long id, OcrDocumentReviewUpdateRequestDto requestDto, Long reviewerMemberId) {
        if (reviewerMemberId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "검수자 정보를 확인할 수 없어.");
        }

        OcrDocument document = getDocumentEntity(id);
        validateReviewUpdate(document, requestDto);

        String reviewedResult = normalizeReviewedResult(document, requestDto);
        document.updateReview(requestDto.getReviewStatus(), reviewedResult, reviewerMemberId);
        return OcrDocumentDetailDto.fromEntity(document, "/api/v1/admin/ocr-documents/" + id + "/file");
    }

    @Transactional
    public OcrDocumentDetailDto retryDocument(Long id) {
        OcrDocument document = getDocumentEntity(id);
        validateRetry(document);

        Path path = Path.of(document.getStoragePath());
        if (!Files.exists(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "재처리할 원본 파일을 찾을 수 없어.");
        }

        try {
            byte[] fileBytes = Files.readAllBytes(path);
            processDocument(document, fileBytes, document.getOriginalFilename(), document.getMimeType(), false);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "재처리용 파일을 읽지 못했어.");
        }

        return OcrDocumentDetailDto.fromEntity(document, "/api/v1/admin/ocr-documents/" + id + "/file");
    }

    @Transactional
    public OcrDocumentDetailDto unlinkDocument(Long id) {
        OcrDocument document = getDocumentEntity(id);
        validateUnlink(document);
        document.markUnlinked();
        return OcrDocumentDetailDto.fromEntity(document, "/api/v1/admin/ocr-documents/" + id + "/file");
    }

    @Transactional
    public PurchaseOrderResponseDto linkPurchaseOrder(Long id, OcrPurchaseOrderLinkRequestDto requestDto, Long actorMemberId) {
        if (actorMemberId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "연결 작업 사용자 정보를 확인할 수 없어.");
        }

        OcrDocument document = getDocumentEntity(id);
        validatePurchaseOrderLink(document, requestDto);

        PurchaseOrderResponseDto responseDto = purchaseService.createPurchaseOrderFromOcrLink(requestDto, actorMemberId);
        document.markLinked(OcrLinkedDomainType.PURCHASE_ORDER, responseDto.getPurchaseOrderId(), actorMemberId);
        return responseDto;
    }

    @Transactional
    public ExpenseResponseDto linkExpense(Long id, OcrExpenseLinkRequestDto requestDto, Long actorMemberId) {
        if (actorMemberId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "연결 작업 사용자 정보를 확인할 수 없어.");
        }

        OcrDocument document = getDocumentEntity(id);
        validateExpenseLink(document, requestDto);

        ExpenseResponseDto responseDto = expenseService.createExpense(
                requestDto.getEmployeeId(),
                requestDto.getExpenseDate(),
                requestDto.getCategory().trim(),
                requestDto.getAmount(),
                requestDto.getDescription().trim(),
                document.getStoragePath(),
                normalizeExpenseStatus(requestDto.getStatus())
        );
        document.markLinked(OcrLinkedDomainType.EXPENSE, responseDto.getId(), actorMemberId);
        return responseDto;
    }

    @Transactional
    public VoucherResponse linkVoucher(Long id, OcrVoucherLinkRequestDto requestDto, Long actorMemberId) {
        if (actorMemberId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "연결 작업 사용자 정보를 확인할 수 없어.");
        }

        OcrDocument document = getDocumentEntity(id);
        validateVoucherLink(document, requestDto);

        VoucherRequest voucherRequest = buildVoucherRequest(document, requestDto);
        VoucherResponse responseDto = voucherService.createVoucher(voucherRequest);
        document.markLinked(OcrLinkedDomainType.VOUCHER, responseDto.getId(), actorMemberId);
        return responseDto;
    }

    @Transactional(readOnly = true)
    public Resource getDocumentFile(Long id) {
        OcrDocument document = getDocumentEntity(id);
        Path path = Path.of(document.getStoragePath());

        if (!Files.exists(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "저장된 증빙 파일을 찾을 수 없어.");
        }

        return new FileSystemResource(path);
    }

    @Transactional
    public void deleteDocument(Long id) {
        OcrDocument document = getDocumentEntity(id);
        if (document.isLinked()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "연결된 OCR 문서는 삭제할 수 없어.");
        }
        Path path = Path.of(document.getStoragePath());

        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "증빙 파일 삭제에 실패했어.");
        }

        ocrDocumentRepository.delete(document);
    }

    @Transactional(readOnly = true)
    public String getDocumentMimeType(Long id) {
        return getDocumentEntity(id).getMimeType();
    }

    @Transactional(readOnly = true)
    public String getDocumentOriginalFilename(Long id) {
        return getDocumentEntity(id).getOriginalFilename();
    }

    private void processDocument(OcrDocument document, byte[] fileBytes, String originalFilename, String contentType, boolean createNewTask) {
        document.markProcessing();

        String taskId = createNewTask
                ? "ocr-doc-" + document.getId()
                : "ocr-doc-retry-" + document.getId() + "-" + System.currentTimeMillis();

        Map<String, Object> taskPayload = Map.of(
                "documentId", document.getId(),
                "documentType", document.getDocumentType().name(),
                "filename", originalFilename
        );
        taskHistoryService.createAiTask(taskId, createNewTask ? "ocr_document_parse" : "ocr_document_retry", taskPayload);

        try {
            Map<String, Object> response = aiClientService.requestOcrDocument(fileBytes, originalFilename, contentType, document.getDocumentType().name());
            Map<String, Object> data = extractData(response);
            String rawResult = toJson(data);
            String vendorName = abbreviate(stringValue(data.get("vendorName")), MAX_EXTRACTED_VENDOR_LENGTH);
            LocalDate transactionDate = parseLocalDate(data.get("transactionDate"));
            BigDecimal totalAmount = parseBigDecimal(data.get("totalAmount"));

            document.markParsed(rawResult, vendorName, transactionDate, totalAmount);
            taskHistoryService.markAiSuccess(taskId, response);
        } catch (RuntimeException exception) {
            log.error("[OCR] OCR parsing failed for document {}", document.getId(), exception);
            String errorMessage = abbreviate(exception.getMessage(), MAX_ERROR_MESSAGE_LENGTH);
            document.markFailed("OCR_PROCESSING_FAILED", errorMessage, null);
            taskHistoryService.markAiFailure(taskId, errorMessage, Map.of(
                    "documentId", document.getId(),
                    "message", errorMessage
            ));
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OCR 분석에 실패했어. 문서함에서 실패 상태를 확인해줘.");
        }
    }

    private OcrDocument getDocumentEntity(Long id) {
        return ocrDocumentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "OCR 문서를 찾을 수 없어."));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "업로드할 파일이 비어 있어.");
        }

        if (file.getSize() > maxFileSizeBytes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "업로드 가능한 최대 파일 크기를 초과했어.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식이야. PNG, JPG, WEBP만 업로드할 수 있어.");
        }
    }

    private Path resolveStorageDirectory() {
        return Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractData(Map<String, Object> response) {
        Object data = response.get("data");
        if (!(data instanceof Map<?, ?> mapData)) {
            throw new RuntimeException("OCR 응답 데이터 형식이 올바르지 않아.");
        }
        return (Map<String, Object>) mapData;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new RuntimeException("OCR 결과 직렬화에 실패했어.", exception);
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private LocalDate parseLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(String.valueOf(value));
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private BigDecimal parseBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String extractExtension(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0) {
            return "";
        }
        return filename.substring(index);
    }

    private String normalizeOriginalFilename(String originalFilename) {
        String cleaned = StringUtils.cleanPath(originalFilename == null ? "upload.bin" : originalFilename).trim();
        if (!hasText(cleaned)) {
            return "upload.bin";
        }

        if (cleaned.length() <= MAX_ORIGINAL_FILENAME_LENGTH) {
            return cleaned;
        }

        String extension = extractExtension(cleaned);
        String nameWithoutExtension = extension.isEmpty()
                ? cleaned
                : cleaned.substring(0, cleaned.length() - extension.length());
        int extensionLength = Math.min(extension.length(), MAX_ORIGINAL_FILENAME_LENGTH - 1);
        String safeExtension = extensionLength > 0 ? extension.substring(extension.length() - extensionLength) : "";
        int baseMaxLength = Math.max(1, MAX_ORIGINAL_FILENAME_LENGTH - safeExtension.length());
        String baseName = nameWithoutExtension.substring(0, Math.min(nameWithoutExtension.length(), baseMaxLength));
        return baseName + safeExtension;
    }

    private void validateReviewUpdate(OcrDocument document, OcrDocumentReviewUpdateRequestDto requestDto) {
        if (document.getProcessingStatus() != OcrProcessingStatus.PARSED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파싱 완료된 OCR 문서만 검수할 수 있어.");
        }

        if (document.getResolvedLinkStatus() == OcrLinkStatus.LINKED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 연결된 OCR 문서는 검수 상태를 바꿀 수 없어.");
        }

        if (requestDto.getReviewStatus() == OcrReviewStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "검수 결과는 APPROVED 또는 REJECTED만 선택할 수 있어.");
        }
    }

    private void validateRetry(OcrDocument document) {
        if (document.isLinked()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 연결된 OCR 문서는 재처리할 수 없어.");
        }
        if (document.getProcessingStatus() != OcrProcessingStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FAILED 상태의 OCR 문서만 재처리할 수 있어.");
        }
    }

    private void validateUnlink(OcrDocument document) {
        if (document.getResolvedLinkStatus() != OcrLinkStatus.LINKED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only linked OCR documents can be unlinked.");
        }
        if (document.getLinkedDomainType() == null || document.getLinkedDomainId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "OCR link metadata is incomplete.");
        }
    }

    private String normalizeReviewedResult(OcrDocument document, OcrDocumentReviewUpdateRequestDto requestDto) {
        String reviewedResult = requestDto.getReviewedResult();

        if (requestDto.getReviewStatus() == OcrReviewStatus.REJECTED) {
            return hasText(reviewedResult) ? reviewedResult.trim() : null;
        }

        if (hasText(reviewedResult)) {
            String trimmed = reviewedResult.trim();
            validateJsonPayload(trimmed);
            return trimmed;
        }

        if (!hasText(document.getRawOcrResult())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "확인할 OCR 결과 원본이 없어.");
        }

        return document.getRawOcrResult();
    }

    private void validateJsonPayload(String payload) {
        try {
            objectMapper.readTree(payload);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "검수 결과는 JSON 형식이어야 해.");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private void validatePurchaseOrderLink(OcrDocument document, OcrPurchaseOrderLinkRequestDto requestDto) {
        validateLinkableDocument(document, "발주");

        if (requestDto == null || requestDto.getVendorId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "발주 연결에는 vendorId가 필요해.");
        }
        if (requestDto.getItems() == null || requestDto.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "발주 품목은 최소 1개 이상 필요해.");
        }
        for (OcrPurchaseOrderLinkRequestDto.Item item : requestDto.getItems()) {
            if (item.getItemId() == null || item.getQuantity() == null || item.getQuantity() <= 0 || item.getUnitPrice() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "발주 품목에는 itemId, quantity, unitPrice가 필요해.");
            }
        }
    }

    private void validateExpenseLink(OcrDocument document, OcrExpenseLinkRequestDto requestDto) {
        validateLinkableDocument(document, "비용");

        if (requestDto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "비용 연결 요청이 비어 있어.");
        }
        if (requestDto.getExpenseDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "비용 연결에는 expenseDate가 필요해.");
        }
        if (!hasText(requestDto.getCategory())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "비용 연결에는 category가 필요해.");
        }
        if (requestDto.getAmount() == null || requestDto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "비용 연결에는 0보다 큰 amount가 필요해.");
        }
        if (!hasText(requestDto.getDescription())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "비용 연결에는 description이 필요해.");
        }
    }

    private void validateVoucherLink(OcrDocument document, OcrVoucherLinkRequestDto requestDto) {
        validateLinkableDocument(document, "전표");

        if (requestDto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "전표 연결 요청이 비어 있어.");
        }
        if (requestDto.getVoucherType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "전표 연결에는 voucherType이 필요해.");
        }
        if (requestDto.getVoucherType() != VoucherType.PURCHASE && requestDto.getVoucherType() != VoucherType.SALES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OCR 전표 연결은 PURCHASE 또는 SALES만 지원해.");
        }
        if (requestDto.getBusinessAccountId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "전표 연결에는 businessAccountId가 필요해.");
        }
        if (requestDto.getSettlementAccountId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "전표 연결에는 settlementAccountId가 필요해.");
        }
        if (requestDto.getSupplyAmount() == null || requestDto.getSupplyAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "전표 연결에는 0보다 큰 supplyAmount가 필요해.");
        }
        if (!hasText(resolveVoucherVendorName(document, requestDto))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "전표 연결에는 vendorNameSnapshot 또는 OCR 거래처명이 필요해.");
        }
        if (requestDto.getVatAmount() != null && requestDto.getVatAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "vatAmount는 음수일 수 없어.");
        }
        if (requestDto.getFeeAmount() != null && requestDto.getFeeAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "feeAmount는 음수일 수 없어.");
        }
    }

    private void validateLinkableDocument(OcrDocument document, String targetLabel) {
        if (document.getProcessingStatus() != OcrProcessingStatus.PARSED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파싱 완료된 OCR 문서만 " + targetLabel + "에 연결할 수 있어.");
        }
        if (document.getReviewStatus() != OcrReviewStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "검수 승인된 OCR 문서만 " + targetLabel + "에 연결할 수 있어.");
        }
        if (document.isLinked()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 다른 도메인에 연결된 OCR 문서야.");
        }
    }

    private VoucherRequest buildVoucherRequest(OcrDocument document, OcrVoucherLinkRequestDto requestDto) {
        VoucherRequest request = new VoucherRequest();
        request.setVoucherDate(requestDto.getVoucherDate());
        request.setVoucherType(requestDto.getVoucherType());
        request.setVatType(requestDto.getVatType());
        request.setVendorId(requestDto.getVendorId());
        request.setVendorNameSnapshot(resolveVoucherVendorName(document, requestDto));
        request.setDescription(resolveVoucherDescription(document, requestDto));
        request.setSupplyAmount(requestDto.getSupplyAmount());
        request.setVatAmount(requestDto.getVatAmount());
        request.setFeeAmount(requestDto.getFeeAmount());
        request.setBusinessAccountId(requestDto.getBusinessAccountId());
        request.setSettlementAccountId(requestDto.getSettlementAccountId());
        return request;
    }

    private String resolveVoucherVendorName(OcrDocument document, OcrVoucherLinkRequestDto requestDto) {
        if (hasText(requestDto.getVendorNameSnapshot())) {
            return requestDto.getVendorNameSnapshot().trim();
        }
        if (hasText(document.getExtractedVendor())) {
            return document.getExtractedVendor().trim();
        }
        return null;
    }

    private String resolveVoucherDescription(OcrDocument document, OcrVoucherLinkRequestDto requestDto) {
        if (hasText(requestDto.getDescription())) {
            return requestDto.getDescription().trim();
        }
        return "OCR 문서 " + document.getId() + " (" + document.getOriginalFilename() + ") 전표 생성";
    }

    private String normalizeExpenseStatus(String status) {
        return hasText(status) ? status.trim() : "PENDING";
    }
}
