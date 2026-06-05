# 재고 ↔ 회계 자동 연동

이 문서는 DDUK ERP 재고관리 도메인의 재고 이동/조정 이벤트가 회계 도메인의 전표(Voucher)를 어떻게 자동으로 생성하는지 설명한다.

---

## 1. 작업 개요

재고 이동 완료(`COMPLETED`) 시 회계 전표(`Voucher`)를 **동일 트랜잭션 내에서 DRAFT 상태로 자동 생성**한다. 비동기 이벤트 방식은 사용하지 않으며, 재고 이동과 전표 생성의 정합성을 단일 트랜잭션이 보장한다.

> [!IMPORTANT]
> 재고 이동과 전표 생성은 항상 동일 트랜잭션 내에서 처리된다. 전표 생성 실패 시 재고 이동 전체가 롤백된다.

---

## 2. 변경 파일 목록

### 신규 생성

- `backend/src/main/java/com/dduk/entity/accounting/voucher/enums/VoucherSourceType.java`
- `backend/src/main/java/com/dduk/service/accounting/inventory/InventoryVoucherService.java`

### 수정

- `backend/src/main/java/com/dduk/entity/accounting/voucher/Voucher.java`
- `backend/src/main/java/com/dduk/entity/accounting/voucher/VoucherLine.java`
- `backend/src/main/java/com/dduk/entity/accounting/voucher/enums/VoucherType.java`
- `backend/src/main/java/com/dduk/service/accounting/AccountingConstants.java`
- `backend/src/main/java/com/dduk/service/inventory/WarehouseTransferService.java`
- `backend/src/main/resources/db/mysql/dduk_bootstrap_schema.sql`
- `backend/src/main/resources/db/mysql/dduk_bootstrap_data.sql`

---

## 3. 재고 → 전표 자동 생성 흐름

```text
WarehouseTransferService.completeTransfer()
  │
  ├─ [1] 멱등성 검증 (이미 COMPLETED면 즉시 반환)
  ├─ [2] 회계기간 마감 검증 (MonthlyClosingService.assertPeriodMutable)
  │       └─ CLOSED 기간이면 IllegalStateException → 전체 롤백
  ├─ [3] 상태 머신 강제 (transfer.complete())
  ├─ [4] 재고 재검증 (Double Verification)
  ├─ [5] 출발 창고 재고 감산 + Inventory 저장
  ├─ [6] TRANSFER_OUT StockMovement 원장 적재
  ├─ [7] 도착 창고 재고 가산 + 이동평균단가 갱신
  ├─ [8] TRANSFER_IN StockMovement 원장 적재
  └─ [9] InventoryVoucherService.createDraftVoucher()
          ├─ TRANSFER_IN/OUT → null 반환 (전표 미생성)
          └─ INBOUND/OUTBOUND/ADJUSTMENT → DRAFT Voucher 생성
```

---

## 4. 회계 전표 생성 규칙

### 4.1. MovementType별 전표 생성 여부

| MovementType | 전표 생성 여부 | 이유 |
| :--- | :--- | :--- |
| `INBOUND` | ✅ 생성 | 재고자산 증가, 외상매입금 발생 |
| `OUTBOUND` | ✅ 생성 | 재고자산 감소, 매출원가 발생 |
| `ADJUSTMENT_IN` | ✅ 생성 | 재고 조정 증가 → 외상매입금 상대계정 (임시) |
| `ADJUSTMENT_OUT` | ✅ 생성 | 재고 조정 감소 → 재고손실 처리 |
| `TRANSFER_IN` | ❌ 미생성 | 회사 내부 자산 이동, 재고자산 총액 불변 |
| `TRANSFER_OUT` | ❌ 미생성 | 회사 내부 자산 이동, 재고자산 총액 불변 |
| `RETURN_IN` | ✅ 생성 | 반품 입고 → INBOUND와 동일 처리 |
| `RETURN_OUT` | ✅ 생성 | 반품 출고 → OUTBOUND와 동일 처리 |

### 4.2. 자동 분개 계정 흐름

> [!NOTE]
> 현재 단계에서는 AccountingConstants 상수 계정을 전체 품목에 동일 적용한다.  
> TODO: `inventory_account_mappings` 테이블 기반 품목 카테고리/창고별 계정 분리 예정

#### 재고 입고 (INBOUND / RETURN_IN)

| 차대 | 계정코드 | 계정명 | 금액 |
| :--- | :--- | :--- | :--- |
| 차변 | 1003 | 재고자산 | `quantity × unitCost` |
| 대변 | 2001 | 외상매입금 | `quantity × unitCost` |

#### 재고 출고 (OUTBOUND / RETURN_OUT)

| 차대 | 계정코드 | 계정명 | 금액 |
| :--- | :--- | :--- | :--- |
| 차변 | 5001 | 매출원가 | `quantity × unitCost` |
| 대변 | 1003 | 재고자산 | `quantity × unitCost` |

#### 재고 조정 감소 (ADJUSTMENT_OUT)

| 차대 | 계정코드 | 계정명 | 금액 |
| :--- | :--- | :--- | :--- |
| 차변 | 5003 | 재고손실 | `quantity × unitCost` |
| 대변 | 1003 | 재고자산 | `quantity × unitCost` |

---

## 5. 전표 추적성 구조

### 5.1. Voucher ↔ StockMovement 연결

```text
stock_movements (원장)
  └── StockMovement.id
      └── vouchers.source_reference_id  (FK-style: 원천 movement ID)
      └── vouchers.source_type = 'STOCK_INBOUND' | 'STOCK_OUTBOUND' | ...
          └── voucher_lines.stock_movement_id  (동일 movement ID)
          └── voucher_lines.movement_type  (MovementType 스냅샷)
          └── voucher_lines.movement_reference_no  (referenceNo 스냅샷)
```

### 5.2. VoucherSourceType Enum

```java
public enum VoucherSourceType {
    STOCK_INBOUND,       // 재고 입고
    STOCK_OUTBOUND,      // 재고 출고
    STOCK_ADJUSTMENT_IN, // 재고 조정 증가
    STOCK_ADJUSTMENT_OUT,// 재고 조정 감소
    PURCHASE_ORDER,      // 발주 확정
    PAYROLL,             // 급여 처리
    MANUAL               // 수동 입력 (null과 동일 취급 가능)
}
```

수동 전표(사용자 직접 입력)는 `sourceType = null`, `sourceReferenceId = null`이다.

---

## 6. 회계기간 마감 차단 정책

```text
completeTransfer() 호출
  → MonthlyClosingService.assertPeriodMutable(LocalDate.now())
  → AccountingPeriod.status == CLOSED or ARCHIVED
  → IllegalStateException 발생
  → @Transactional rollback
  → 재고 이동 없음, 전표 생성 없음
```

> [!WARNING]
> 회계기간 마감(CLOSED) 상태에서는 재고 이동 완료가 **불가능**하다.  
> 재오픈(REOPENED) 이후에만 처리 가능하다.

---

## 7. Schema 변경 내역

### 7.1. `vouchers` 테이블 컬럼 추가

| 컬럼명 | 타입 | Null | 설명 |
| :--- | :--- | :--- | :--- |
| `source_type` | VARCHAR(50) | NULL | 자동생성 원천 도메인 (VoucherSourceType enum) |
| `source_reference_id` | BIGINT | NULL | 원천 엔티티 ID (stock_movements.id 등) |

### 7.2. `voucher_lines` 테이블 컬럼 추가

| 컬럼명 | 타입 | Null | 설명 |
| :--- | :--- | :--- | :--- |
| `stock_movement_id` | BIGINT | NULL | 연결된 StockMovement ID |
| `movement_type` | VARCHAR(50) | NULL | MovementType 스냅샷 (감사 추적용) |
| `movement_reference_no` | VARCHAR(50) | NULL | 원장 참조번호 스냅샷 (감사 추적용) |

### 7.3. `accounts` 시드 추가

| 코드 | 계정명 | 유형 |
| :--- | :--- | :--- |
| 1003 | 재고자산 | ASSET |
| 5003 | 재고손실 | EXPENSE |

---

## 8. 향후 확장 포인트

- `inventory_account_mappings` 테이블: 품목 카테고리/브랜드/창고별 계정 분리
- COGS(매출원가) 자동 계산 및 매출 연동
- 월 마감 자동화: 기간 내 미승인 재고 전표 일괄 처리
- 재고 평가 리포트 (이동평균법, FIFO 비교)

---

## 9. 관련 문서 (Cross-reference)

- `docs/accounting/ACCOUNTING_TRANSACTION.md` — 전표 수동 입력 및 자동 분개 구조
- `docs/accounting/ACCOUNTING_MONTH_END.md` — 회계기간 마감 정책
- `docs/inventory/STOCK_MOVEMENT.md` — 재고 원장 구조 및 창고 이동 워크플로우

---

## 10. 변경 이력 (Change Log)

| 버전 | 일자 | 작성자 | 변경 내용 |
| :--- | :--- | :--- | :--- |
| v1.0 | 2026-05-27 | Antigravity AI | 재고 ↔ 회계 자동 연동 초기 구현 문서 |
