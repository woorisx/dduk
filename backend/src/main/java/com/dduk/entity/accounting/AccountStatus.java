package com.dduk.entity.accounting;

/**
 * 계정과목 관리 상태 Enum
 */
public enum AccountStatus {
    ACTIVE,    // 활성 (사용 가능)
    INACTIVE,  // 비활성 (미사용)
    LOCKED     // 잠금 (구조 변경 불가능, 전표 기표는 allowPosting에 따라 가능)
}
