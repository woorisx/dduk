package com.dduk.service.accounting;

/**
 * 회계 도메인 공통 상수 정의.
 *
 * <p>계정 코드는 dduk_bootstrap_schema.sql accounts 테이블의 시스템 계정과 동기화됩니다.
 * 계정 코드를 직접 참조하는 로직은 이 클래스를 통해서만 접근해야 합니다.
 *
 * <p>TODO: inventory_account_mappings 테이블 기반 품목 카테고리별 계정 분리 예정.
 *          현재는 단일 재고자산(1003), 매출원가(5001) 계정을 모든 재고 유형에 적용합니다.
 */
public class AccountingConstants {

    // ─── 자산 계정 (ASSET) ───────────────────────────────────────────
    public static final String CASH = "1001";
    public static final String BANK_ACCOUNT = "1002";
    /** 재고자산 계정. TODO: 품목 카테고리/브랜드/창고별 분리 예정 */
    public static final String INVENTORY_ASSET = "1003";
    public static final String ACCOUNTS_RECEIVABLE = "1004";

    // ─── 부채 계정 (LIABILITY) ───────────────────────────────────────
    public static final String ACCOUNTS_PAYABLE = "2001";
    public static final String UNPAID_AMOUNT = "2002";
    public static final String SALARY_PAYABLE = "2003";
    public static final String WITHHOLDING_PAYABLE = "2004";

    // ─── 수익 계정 (REVENUE) ─────────────────────────────────────────
    public static final String SALES_REVENUE = "4001";

    // ─── 비용 계정 (EXPENSE) ─────────────────────────────────────────
    /** 매출원가 계정. TODO: 품목 카테고리별 분리 예정 */
    public static final String COST_OF_SALES = "5001";
    public static final String PAYROLL_EXPENSE = "5002";
    public static final String WELFARE_EXPENSE = "5003";
    /** 재고 조정 손실 계정 (ADJUSTMENT_OUT 시) */
    public static final String INVENTORY_LOSS = "5003";

    // ─── 분개 상태 ────────────────────────────────────────────────────
    public static final String JOURNAL_STATUS_DRAFT = "DRAFT";
    public static final String JOURNAL_STATUS_POSTED = "POSTED";
    public static final String JOURNAL_STATUS_CANCELLED = "CANCELLED";

    // ─── 차대 구분 ────────────────────────────────────────────────────
    public static final String SIDE_DEBIT = "DEBIT";
    public static final String SIDE_CREDIT = "CREDIT";

    // ─── 자동분개 원천 식별자 ─────────────────────────────────────────
    public static final String SOURCE_PAYROLL = "PAYROLL";
    public static final String SOURCE_PURCHASE = "PURCHASE_ORDER";
    public static final String SOURCE_STOCK_INBOUND = "STOCK_INBOUND";
    public static final String SOURCE_STOCK_OUTBOUND = "STOCK_OUTBOUND";
    public static final String SOURCE_ADJUSTMENT_IN = "STOCK_ADJUSTMENT_IN";
    public static final String SOURCE_ADJUSTMENT_OUT = "STOCK_ADJUSTMENT_OUT";
    public static final String SOURCE_MANUAL = "MANUAL";

    // ─── 회계기간 상태 ────────────────────────────────────────────────
    public static final String PERIOD_OPEN = "OPEN";
    public static final String PERIOD_CLOSED = "CLOSED";

    private AccountingConstants() {
    }
}
