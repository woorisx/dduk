package com.dduk.dto.accounting.period;

import com.dduk.entity.accounting.period.AccountingPeriodStatus;
import com.dduk.entity.accounting.period.ClosingActionType;
import com.dduk.entity.accounting.period.ClosingLog;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ClosingLogResponse {
    private Long id;
    private String periodKey;
    private ClosingActionType actionType;
    private AccountingPeriodStatus fromStatus;
    private AccountingPeriodStatus toStatus;
    private String actor;
    private LocalDateTime actionAt;
    private String ipAddress;
    private String message;

    public static ClosingLogResponse from(ClosingLog log) {
        return ClosingLogResponse.builder()
                .id(log.getId())
                .periodKey(log.getAccountingPeriod().getPeriodKey())
                .actionType(log.getActionType())
                .fromStatus(log.getFromStatus())
                .toStatus(log.getToStatus())
                .actor(log.getActor())
                .actionAt(log.getActionAt())
                .ipAddress(log.getIpAddress())
                .message(log.getMessage())
                .build();
    }
}
