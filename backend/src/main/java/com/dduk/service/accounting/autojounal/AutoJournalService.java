package com.dduk.service.accounting.autojounal;

import com.dduk.dto.accounting.JournalLineRequest;
import com.dduk.entity.accounting.JournalEntry;
import com.dduk.service.accounting.AccountingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 자동분개 서비스
 * - JournalStrategy 구현체를 자동 등록
 * - 원천 도메인 이벤트에 맞는 전략을 선택하여 전표 자동 생성
 */
@Service
@RequiredArgsConstructor
public class AutoJournalService {

    private final AccountingService accountingService;
    private final List<JournalStrategy> strategies;

    private Map<String, JournalStrategy> strategyMap;

    /** 초기화: 전략 목록을 sourceType 키로 맵핑 */
    @jakarta.annotation.PostConstruct
    public void init() {
        strategyMap = strategies.stream()
                .collect(Collectors.toMap(JournalStrategy::getSupportedSourceType, Function.identity()));
    }

    /**
     * 자동분개 실행
     * - 원본 트랜잭션과 동일한 트랜잭션 내에서 수행 (정합성 보장)
     * @param sourceType 원천 도메인 식별자 (예: PAYROLL, PURCHASE_ORDER)
     * @param sourceId   원천 엔티티 ID
     * @param source     원천 엔티티 객체
     * @return 생성된 JournalEntry (POSTED 상태)
     */
    @Transactional
    public JournalEntry createAndPostJournal(String sourceType, Long sourceId, Object source) {
        JournalStrategy strategy = strategyMap.get(sourceType);
        if (strategy == null) {
            throw new IllegalArgumentException("지원하지 않는 자동분개 소스 유형입니다: " + sourceType);
        }

        List<JournalLineRequest> lines = strategy.buildLines(source);
        String description = strategy.buildDescription(source);
        var date = strategy.getJournalDate(source);

        return accountingService.createAndPostJournal(date, description, lines, sourceType, sourceId);
    }
}
