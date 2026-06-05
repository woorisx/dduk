package com.dduk.entity.accounting.voucher.enums;

/**
 * 전표 자동 생성 원천 도메인 식별자 Enum
 *
 * <p>회계 전표가 어떤 도메인 이벤트에 의해 자동 생성되었는지를 추적합니다.
 * 문자열 하드코딩 없이 Enum 타입으로 관리하여 타입 안전성과 감사 추적성을 보장합니다.
 *
 * <p>수동 전표(사용자 직접 입력)의 경우 sourceType = null 입니다.
 */
public enum VoucherSourceType {

    /** 재고 입고 (매입 수령 완료 시 자동 생성) */
    STOCK_INBOUND,

    /** 재고 출고 (판매 출하 시 자동 생성) */
    STOCK_OUTBOUND,

    /** 재고 조정 증가 (실사 등으로 재고를 늘릴 때) */
    STOCK_ADJUSTMENT_IN,

    /** 재고 조정 감소 (실사 등으로 재고를 줄일 때) */
    STOCK_ADJUSTMENT_OUT,

    /** 발주 확정 (PurchaseOrder 완료 시) */
    PURCHASE_ORDER,

    /** 급여 처리 (PayrollLedger 확정 시) */
    PAYROLL,

    /** 수동 입력 (사용자가 직접 전표 작성) */
    MANUAL
}
