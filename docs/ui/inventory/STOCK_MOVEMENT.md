# Stock Movement

이 문서는 DDUK ERP 재고관리 도메인의 핵심 원장인 재고 변동 원장(`stock_movements`)과 창고 이동 승인 워크플로우(`warehouse_transfers`) 설명서다. 데이터의 무결성, 멱등성(Idempotency), 낙관적 락(@Version) 기반 동시성 통제 및 상태 머신 흐름을 설명한다.

---

## 1. 작업 개요

재고의 모든 유입과 유출은 수정과 삭제가 불가능한 **Immutable Ledger (불변 원장)** 구조로 기록된다. 모든 창고 이동은 요청 - 승인 - 완료 - 취소의 명확한 상태 머신 단계와 안전장치(이중 재검증 및 멱등성 보장)를 통해 처리되며, 단일 서비스 트랜잭션 안에서 원자성을 강하게 보장받는다.

---

## 2. 변경 파일 목록

- `backend/src/main/java/com/dduk/entity/inventory/StockMovement.java`
- `backend/src/main/java/com/dduk/entity/inventory/WarehouseTransfer.java`
- `backend/src/main/java/com/dduk/entity/inventory/WarehouseTransferItem.java`
- `backend/src/main/java/com/dduk/service/inventory/WarehouseTransferService.java`
- `backend/src/main/java/com/dduk/controller/inventory/WarehouseTransferController.java`
- `backend/src/main/resources/db/mysql/dduk_bootstrap_data.sql`
- `frontend/pages/inventory/transfers.html`
- `frontend/pages/inventory/movements.html`

---

## 3. API 설명

기본 경로는 `/api/v1`이다.

- `POST /api/v1/warehouse-transfers`
  - 창고 간 재고 이동 요청 등록 (`status = PENDING`)
- `GET /api/v1/warehouse-transfers`
  - 창고 이동 요청 목록 조회 (상태 필터 지원)
- `GET /api/v1/warehouse-transfers/{id}`
  - 특정 창고 이동 건의 상세 항목 및 품목 목록 조회
- `POST /api/v1/warehouse-transfers/{id}/approve`
  - 창고 이동 요청 승인 (`PENDING -> APPROVED`)
- `POST /api/v1/warehouse-transfers/{id}/complete`
  - 창고 이동 수령 완료 및 원장 가감 처리 (`APPROVED -> COMPLETED`)
- `POST /api/v1/warehouse-transfers/{id}/cancel`
  - 창고 이동 요청 취소 또는 반려 (`status = CANCELLED`)
- `GET /api/v1/inventories/stock-movements`
  - 전체 재고 변동 원장 거래 이력 조회

응답은 하네스 공통 규격인 `status`, `data`, `message` 구조를 사용한다.

---

## 4. 핵심 데이터 및 흐름 제어

### 4.1. 상태 머신 흐름 및 예약재고 제어
```
[REQUESTED / PENDING] ──(approve)──> [APPROVED] ──(complete)──> [COMPLETED]
         │                                                            │
      (cancel) ───────────────────────────────────────────────────────┘
         └─────────────────────────> [CANCELLED]
```

1. **이동 요청 (`PENDING`)**
   - 출발 창고의 가용재고를 검증한다.
   - 요청 수량만큼 출발 창고의 `allocated_stock`을 차감/가산하여 선점(잠금) 처리한다.
   - 현재고(`current_stock`)는 변동시키지 않는다.
2. **이동 승인 (`APPROVED`)**
   - 승인자 및 승인일시를 기록하고 상태를 승인 상태로 격상한다.
3. **이동 완료 (`COMPLETED`)**
   - **이중 재검증 (Double Verification)**: 완료 시점에 출발 창고의 현재 재고 수량을 재조회하여, 이동 예정인 수량이 현재고보다 많다면 `IllegalStateException`을 유발하여 트랜잭션을 전면 롤백한다.
   - **원자적 가감 및 원장 적재**:
     - 출발 창고: `current_stock` 차감, `allocated_stock` 차감
     - 도착 창고: `current_stock` 가산, `average_cost` (이동평균단가) 및 `inventory_value` 재산출
     - 양방향 이력 적재: `TRANSFER_OUT` (출발창고 변동) 및 `TRANSFER_IN` (도착창고 변동) 원장을 각각 불변(`stock_movements`) 테이블에 적재한다.
4. **취소 및 반려 (`CANCELLED`)**
   - `PENDING` 또는 `APPROVED` 상태인 경우에만 취소가 가능하며, 취소 시 출발 창고의 `allocated_stock`을 요청 수량만큼 즉시 해제(감산)하여 가용재고를 원복시킨다.

---

## 5. 정합성 및 안전 장치

- **멱등성 (Idempotency) 보장**:
  - 이미 `COMPLETED` 상태인 건에 대해 `complete` API가 중복 호출되더라도, 추가적인 재고 변동이나 원장 가감 없이 성공 응답을 그대로 반환하여 중복 반영을 방지한다.
- **낙관적 락 (@Version)**:
  - `inventories` 테이블에 낙관적 락 버전(`version`) 컬럼을 완벽 적용하여 대규모 분산/동시성 상황 속에서 동시 차감 발생 시 발생할 수 있는 데이터 정합성 깨짐을 완벽 차단한다.
- **단일 트랜잭션 보장**:
  - 모든 재고 차감, 가산, 상태 전이, 원장 적재 행위는 `@Transactional(rollbackFor = Exception.class)` 내부에서 동기식으로 실행된다.

---

## 6. 재고 ↔ 회계 자동 연동 (구현 완료)

재고 이동 완료(`COMPLETED`) 시 동일 트랜잭션 내에서 회계 전표가 자동 DRAFT 생성된다.

```text
WarehouseTransferService.completeTransfer()
  └─ InventoryVoucherService.createDraftVoucher(movement)
      ├─ TRANSFER_IN/OUT → 전표 미생성 (내부 자산 이동)
      └─ INBOUND/OUTBOUND/ADJUSTMENT → DRAFT Voucher 자동 생성
```

> [!IMPORTANT]
> 재고 이동과 전표 생성은 항상 동일 트랜잭션에서 처리된다.  
> 전표 생성 실패 시 재고 이동 전체가 롤백된다.

자세한 분개 규칙 및 Schema 변경은 `docs/inventory/INVENTORY_ACCOUNTING.md`를 참조한다.

---

## 7. 테스트 방법

### 통합 테스트 구동
```bash
cd backend
./gradlew.bat test --tests "com.dduk.service.inventory.WarehouseTransferServiceTest"
```

### 프론트 확인 방법
1. Spring Boot 서버를 기동하고 `frontend/pages/inventory/transfers.html`을 연다.
2. [창고 이동 요청] 버튼을 눌러 양식을 제출하고 목록에서 PENDING 상태를 확인한다.
3. 관리자 권한으로 로그인 후 [승인] 처리를 수행하여 APPROVED 상태 전이를 확인한다.
4. 도착 창고 기준 [수령 완료]를 수행하고, `movements.html` 메뉴로 이동하여 `TRANSFER_OUT`과 `TRANSFER_IN` 이력이 양방향으로 적재되었는지 확인한다.
5. 전표 관리 화면(`voucher_management.html`)에서 `VoucherType=INVENTORY`, `status=DRAFT`인 전표가 자동 생성되었는지 확인한다.

---

## 8. 변경 이력 (Change Log)

| 버전 | 일자 | 작성자 | 변경 내용 |
| :--- | :--- | :--- | :--- |
| v1.0 | 2026-05-27 | Antigravity AI | 최초 문서 작성 및 규격 통일 |
| v1.1 | 2026-05-27 | Antigravity AI | 재고 ↔ 회계 자동 연동 구현 완료 반영 및 cross-reference 추가 |
