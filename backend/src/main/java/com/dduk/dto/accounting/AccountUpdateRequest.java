package com.dduk.dto.accounting;

import com.dduk.entity.accounting.AccountStatus;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountUpdateRequest {
    private String name;
    private String englishName;
    private Integer sortOrder;
    private String description;
    private AccountStatus status;
    private Boolean allowPosting;
    private Long parentId;
}
