package com.dduk.service.admin;

import com.dduk.service.admin.taskhistory.TaskHistoryService;
import com.dduk.dto.admin.AdminDashboardResponseDto;
import com.dduk.entity.admin.TaskHistoryType;
import com.dduk.entity.inventory.PurchaseStatus;
import com.dduk.repository.accounting.AccountRepository;
import com.dduk.repository.accounting.JournalEntryRepository;
import com.dduk.repository.admin.MemberRepository;
import com.dduk.repository.hr.EmployeeRepository;
import com.dduk.repository.inventory.ItemRepository;
import com.dduk.repository.inventory.PurchaseOrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

@Slf4j
@Service
@Transactional(readOnly = true)
public class AdminDashboardService {

    private static final int RECENT_LOGIN_WINDOW_DAYS = 7;

    private final MemberRepository memberRepository;
    private final EmployeeRepository employeeRepository;
    private final ItemRepository itemRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final TaskHistoryService taskHistoryService;
    private final RestTemplate restTemplate;

    @Value("${ai-server.url:http://localhost:5000}")
    private String aiServerUrl;

    @Value("${rpa-server.url:http://localhost:5500}")
    private String rpaServerUrl;

    public AdminDashboardService(
            MemberRepository memberRepository,
            EmployeeRepository employeeRepository,
            ItemRepository itemRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            AccountRepository accountRepository,
            JournalEntryRepository journalEntryRepository,
            TaskHistoryService taskHistoryService,
            RestTemplateBuilder restTemplateBuilder
    ) {
        this.memberRepository = memberRepository;
        this.employeeRepository = employeeRepository;
        this.itemRepository = itemRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.taskHistoryService = taskHistoryService;
        this.restTemplate = restTemplateBuilder.build();
    }

    public AdminDashboardResponseDto getDashboard() {
        LocalDateTime recentLoginThreshold = LocalDateTime.now().minusDays(RECENT_LOGIN_WINDOW_DAYS);
        LocalDateTime recentFailureThreshold = LocalDateTime.now().minusDays(RECENT_LOGIN_WINDOW_DAYS);

        return AdminDashboardResponseDto.builder()
                .accountSummary(AdminDashboardResponseDto.AccountSummary.builder()
                        .totalMemberCount(memberRepository.count())
                        .activeMemberCount(memberRepository.countByActiveTrue())
                        .recentLoginCount(memberRepository.countByLastLoginAtAfter(recentLoginThreshold))
                        .recentLoginWindowDays(RECENT_LOGIN_WINDOW_DAYS)
                        .build())
                .domainSummary(AdminDashboardResponseDto.DomainSummary.builder()
                        .hr(AdminDashboardResponseDto.HrSummary.builder()
                                .employeeCount(employeeRepository.count())
                                .build())
                        .inventory(AdminDashboardResponseDto.InventorySummary.builder()
                                .itemCount(itemRepository.count())
                                .purchaseOrderCount(purchaseOrderRepository.count())
                                .approvedPurchaseOrderCount(purchaseOrderRepository.countByStatus(PurchaseStatus.APPROVED))
                                .build())
                        .accounting(AdminDashboardResponseDto.AccountingSummary.builder()
                                .accountCount(accountRepository.count())
                                .journalEntryCount(journalEntryRepository.count())
                                .build())
                        .build())
                .infraSummary(AdminDashboardResponseDto.InfraSummary.builder()
                        .aiRequests(taskHistoryService.countTasksByType(TaskHistoryType.AI))
                        .rpaSuccessRate(taskHistoryService.calculateSuccessRate(TaskHistoryType.RPA))
                        .activeChatSessions(taskHistoryService.countActiveTasksByType(TaskHistoryType.AI))
                        .rpaQueueCount(taskHistoryService.countActiveTasksByType(TaskHistoryType.RPA))
                        .aiEngineStatus(checkHealthStatus(aiServerUrl, "AI"))
                        .rpaNodeStatus(checkHealthStatus(rpaServerUrl, "RPA"))
                        .recentErrorCount(taskHistoryService.countRecentFailures(recentFailureThreshold) + "건")
                        .build())
                .build();
    }

    private String checkHealthStatus(String baseUrl, String label) {
        String healthUrl = buildHealthUrl(baseUrl);

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(healthUrl, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                return "정상";
            }
            log.warn("[Admin Dashboard] {} health check returned status {}", label, response.getStatusCode());
            return "주의";
        } catch (Exception exception) {
            log.warn("[Admin Dashboard] {} health check failed for {}", label, healthUrl, exception);
            return "오프라인";
        }
    }

    private String buildHealthUrl(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl + "health";
        }
        return baseUrl + "/health";
    }
}
