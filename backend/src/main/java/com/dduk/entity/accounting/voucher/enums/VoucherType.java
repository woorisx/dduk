package com.dduk.entity.accounting.voucher.enums;

public enum VoucherType {
    SALES, PURCHASE, GENERAL, CORRECTION, CLEARING,
    /** 재고 이동/조정 자동 생성 전표 (STOCK_INBOUND, OUTBOUND, ADJUSTMENT 등) */
    INVENTORY
}
