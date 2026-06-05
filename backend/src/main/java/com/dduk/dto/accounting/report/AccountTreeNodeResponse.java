package com.dduk.dto.accounting.report;

import com.dduk.entity.accounting.Account;
import com.dduk.entity.accounting.AccountType;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
public class AccountTreeNodeResponse {
    private Long id;
    private Long parentId;
    private String code;
    private String name;
    private AccountType type;
    private Integer level;
    private boolean leaf;
    @Builder.Default
    private List<AccountTreeNodeResponse> children = new ArrayList<>();

    public static AccountTreeNodeResponse from(Account account) {
        return AccountTreeNodeResponse.builder()
                .id(account.getId())
                .parentId(account.getParentAccount() != null ? account.getParentAccount().getId() : null)
                .code(account.getCode())
                .name(account.getName())
                .type(account.getType())
                .level(account.getLevel())
                .leaf(account.isLeaf())
                .children(new ArrayList<>())
                .build();
    }
}
