package com.dduk.service.accounting.autojounal;

import com.dduk.dto.accounting.JournalLineRequest;

import java.time.LocalDate;
import java.util.List;

/**
 * 자동분개 전략 인터페이스
 * - 도메인 이벤트별로 구현체를 제공
 * - 소스 유형 식별자와 분개 라인 생성을 담당
 */
public interface JournalStrategy {

    /** 이 전략이 처리하는 소스 유형 식별자 (예: PAYROLL, PURCHASE_ORDER) */
    String getSupportedSourceType();

    /** 전표 날짜 */
    LocalDate getJournalDate(Object source);

    /** 전표 적요 생성 */
    String buildDescription(Object source);

    /** 분개 라인 목록 생성 */
    List<JournalLineRequest> buildLines(Object source);
}
