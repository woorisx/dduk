package com.dduk.dto.accounting;

import com.dduk.entity.accounting.Account;
import com.dduk.entity.accounting.AccountSide;
import com.dduk.entity.accounting.AccountStatus;
import com.dduk.entity.accounting.AccountType;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountResponse {
    private Long id;
    private String code;
    private String name;
    private String englishName;
    private AccountType type;
    private AccountSide normalBalance;
    private Integer level;
    private Integer sortOrder;
    private String description;
    private AccountStatus status;
    private Boolean allowPosting;
    private Boolean systemAccount;
    private String parentCode;
    private Long parentId;
    private boolean isLeaf;
    
    @Builder.Default
    private List<AccountResponse> children = new ArrayList<>();

    public static AccountResponse from(Account account) {
        if (account == null) return null;
        
        return AccountResponse.builder()
                .id(account.getId())
                .code(account.getCode())
                .name(account.getName())
                .englishName(account.getEnglishName())
                .type(account.getType())
                .normalBalance(account.getNormalBalance())
                .level(account.getLevel())
                .sortOrder(account.getSortOrder())
                .description(account.getDescription())
                .status(account.getStatus())
                .allowPosting(account.getAllowPosting())
                .systemAccount(account.getSystemAccount())
                .parentCode(account.getParentCode())
                .parentId(account.getParentAccount() != null ? account.getParentAccount().getId() : null)
                .isLeaf(account.isLeaf())
                .build();
    }

    public static AccountResponse fromWithChildren(Account account) {
        if (account == null) return null;
        
        AccountResponse response = from(account);
        if (account.getChildren() != null) {
            response.setChildren(
                account.getChildren().stream()
                    .filter(child -> !Boolean.TRUE.equals(child.getDeleted()))
                    .map(AccountResponse::fromWithChildren)
                    .collect(Collectors.toList())
            );
        }
        return response;
    }
}
