package com.dduk.dto.accounting;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ExpenseStatusUpdateRequest {

    private String status;
}
