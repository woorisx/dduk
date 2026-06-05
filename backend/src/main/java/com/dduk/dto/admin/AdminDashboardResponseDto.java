package com.dduk.dto.admin;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminDashboardResponseDto {
    private AccountSummary accountSummary;
    private DomainSummary domainSummary;
    private InfraSummary infraSummary;

    @Getter
    @Builder
    public static class AccountSummary {
        private long totalMemberCount;
        private long activeMemberCount;
        private long recentLoginCount;
        private int recentLoginWindowDays;
    }

    @Getter
    @Builder
    public static class DomainSummary {
        private HrSummary hr;
        private InventorySummary inventory;
        private AccountingSummary accounting;
    }

    @Getter
    @Builder
    public static class HrSummary {
        private long employeeCount;
    }

    @Getter
    @Builder
    public static class InventorySummary {
        private long itemCount;
        private long purchaseOrderCount;
        private long approvedPurchaseOrderCount;
    }

    @Getter
    @Builder
    public static class AccountingSummary {
        private long accountCount;
        private long journalEntryCount;
    }

    @Getter
    @Builder
    public static class InfraSummary {
        private long aiRequests;
        private String rpaSuccessRate;
        private long activeChatSessions;
        private long rpaQueueCount;
        private String aiEngineStatus;
        private String rpaNodeStatus;
        private String recentErrorCount;
    }
}
