package com.dduk.dto.accounting.period;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClosingActionRequest {
    private String actor;
    private String ipAddress;
    private Boolean force;
}
