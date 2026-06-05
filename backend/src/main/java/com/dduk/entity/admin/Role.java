package com.dduk.entity.admin;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Role {
    ADMIN("ROLE_ADMIN", "관리자"),
    HR("ROLE_HR", "인사/회계"),
    INVENTORY("ROLE_INVENTORY", "구매/재고");

    private final String key;
    private final String title;
}
