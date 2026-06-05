package com.dduk.dto.accounting;

import com.dduk.entity.accounting.AccountSide;
import com.dduk.entity.accounting.AccountStatus;
import com.dduk.entity.accounting.AccountType;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountCreateRequest {
    private String code;
    private String name;
    private String englishName;
    private AccountType type;
    private AccountSide normalBalance;
    private Integer sortOrder;
    private String description;
    private AccountStatus status;
    private Boolean allowPosting;
    private Long parentId;
}
