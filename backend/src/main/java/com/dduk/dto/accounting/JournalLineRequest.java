package com.dduk.dto.accounting;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 전표 라인 생성 요청 DTO (서비스 내부 사용)
 */
@Data
public class JournalLineRequest {
    private String accountCode;
    private BigDecimal debitAmount;
    private BigDecimal creditAmount;
    private String description;
    private String referenceType;
    private Long referenceId;
}
