package com.dduk.service.accounting;

import com.dduk.repository.accounting.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class ChartOfAccountsInitializer {

    private final AccountManagementService accountManagementService;
    private final AccountRepository accountRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        log.info("계정과목(CoA) 마스터 데이터 검증 및 적재를 시작합니다...");
        try {
            accountManagementService.seedDefaultChartOfAccounts();
            long count = accountRepository.count();
            log.info("계정과목(CoA) 마스터 데이터 동기화 완료. 현재 총 계정 수: {}", count);
        } catch (Exception e) {
            log.error("계정과목(CoA) 마스터 데이터 초기 적재 중 오류 발생", e);
        }
    }
}
