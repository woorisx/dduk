package com.dduk.dto.accounting.dashboard;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class RecentAccountingActivityResponse {
    private String activityType;
    private String target;
    private String actor;
    private LocalDateTime activityAt;
    private String status;
}
