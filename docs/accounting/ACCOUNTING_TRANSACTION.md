# Accounting Transaction

이 문서는 기존 `ACCOUNTING_VOUCHER_MANAGEMENT.md`를 `docs/accounting/ACCOUNTING_TRANSACTION.md`로 이동한 문서다.

DDUK ERP 회계관리의 단순 입출금 입력 화면을 ERP 스타일 전표 관리 구조로 리팩토링했다. 본 구현은 기존 Chart of Accounts의 `Account`, 계정 트리, `AccountType`, `AccountSide`, 말단 계정 구조를 기준으로 동작한다.

## 작업 개요

- 메뉴/화면명을 `전표 관리`로 변경했다.
- 상단 탭은 `매출전표`, `매입전표`를 우선 지원하고 `일반전표`, `수정전표`, `반제전표`는 확장 슬롯으로 표시한다.
- 사용자는 공급가액, 부가세, 거래처, 매출/매입 계정, 입금/출금 계좌를 입력한다.
- 저장 시 `Voucher`와 `VoucherLine`을 저장하고, 동시에 `JournalEntry`와 `JournalLine`을 자동 생성한다.
- 차변 합계와 대변 합계가 일치하지 않으면 저장을 차단한다.
- 승인 흐름은 `DRAFT -> REQUESTED -> APPROVED -> POSTED`를 지원한다.

## 변경 파일 목록

- `backend/src/main/java/com/dduk/controller/accounting/voucher/VoucherController.java`
- `backend/src/main/java/com/dduk/service/accounting/voucher/VoucherService.java`
- `backend/src/main/java/com/dduk/repository/accounting/voucher/VoucherRepository.java`
- `backend/src/main/java/com/dduk/repository/accounting/AccountRepository.java`
- `backend/src/main/java/com/dduk/dto/accounting/voucher/VoucherRequest.java`
- `backend/src/main/java/com/dduk/dto/accounting/voucher/VoucherSummaryResponse.java`
- `frontend/pages/hr/accounting/voucher_management.html`
- `frontend/assets/js/accounting/voucher_management.js`
- `frontend/styles/accounting/voucher-management.css`
- `docs/ACCOUNTING_VOUCHER_MANAGEMENT.md`

## API 설명

기본 경로: `/api/accounting/vouchers`

호환 경로: `/api/v1/accounting/vouchers`

| Method | Path | 설명 |
| --- | --- | --- |
| `GET` | `/api/v1/accounting/vouchers?type=SALES` | 전표 목록 조회. `type`은 선택값이다. |
| `GET` | `/api/v1/accounting/vouchers/summary` | 오늘 등록, 상태별 건수, 공급가액/VAT/합계 요약 조회 |
| `GET` | `/api/v1/accounting/vouchers/accounts/search` | 계정과목 검색. `keyword`, `type`, `cashOnly` 지원 |
| `GET` | `/api/v1/accounting/vouchers/accounts/tree-search` | 계층형 표시용 계정과목 검색. `level`, `parentCode` 포함 |
| `GET` | `/api/v1/accounting/accounts/search` | Account Management 기준 계정과목 검색. `keyword`, `type`, `cashOnly` 지원 |
| `GET` | `/api/accounting/accounts/search` | v1 없는 호환 계정과목 검색 경로 |
| `POST` | `/api/v1/accounting/vouchers` | 전표 저장 및 자동 분개 생성 |
| `PATCH` | `/api/v1/accounting/vouchers/{id}/status?status=REQUESTED` | 전표 상태 변경 |

계정 검색 규칙:

- 일반 계정 검색은 `deleted=false`, `status=ACTIVE`, `Account.allowPosting = true`, 말단 계정(`children is empty`)만 반환한다.
- 매출계정은 프론트에서 `AccountType=REVENUE`로 조회한다.
- 매입계정은 프론트에서 `AccountType=EXPENSE`로 조회한다.
- 입금/출금 계좌는 `cashOnly=true`로 조회하며 `AccountType=ASSET` 중 `111%` 현금및현금성자산 계열과 레거시 현금/예금 계정만 반환한다.
- 코드, 계정명, 영문명 검색을 지원한다.
- 초성 검색은 사용자가 `ㄱ-ㅎ` 초성 문자열을 입력한 경우 서비스 레이어에서 계정명 초성을 계산해 필터링한다.

## Summary Aggregation

`GET /api/accounting/vouchers/summary` 응답 예시:

```json
{
  "status": "success",
  "data": {
    "todayCount": 1,
    "totalVoucherCount": 1,
    "draftCount": 0,
    "requestedCount": 1,
    "approvedCount": 0,
    "postedCount": 0,
    "totalSupplyAmount": 100000.00,
    "totalVatAmount": 10000.00,
    "totalAmount": 110000.00
  }
}
```

집계 방식:

- 총 전표 수: `vouchers` 건수
- `DRAFT`, `REQUESTED`, `APPROVED`, `POSTED`: `Voucher.status`별 건수
- 공급가액: `voucher_lines.supply_amount` 합계
- 부가세: `voucher_lines.vat_amount` 합계
- 총 거래 금액: 차변(`debitCredit=DEBIT`) `voucher_lines.total_amount` 합계

차변 라인 기준으로 총 거래 금액을 계산하는 이유는 자동 분개 구조상 차변과 대변 라인이 동시에 저장되므로 전체 라인의 `total_amount`를 단순 합산하면 금액이 중복되기 때문이다.

## Entity 구조

### Voucher

- `voucherNo`: 전표번호
- `voucherDate`: 전표일자
- `voucherType`: `SALES`, `PURCHASE`, `GENERAL`, `CORRECTION`, `CLEARING`
- `vatType`: 부가세 유형
- `vendorId`, `vendorNameSnapshot`: 거래처 참조 및 스냅샷
- `status`: `DRAFT`, `REQUESTED`, `APPROVED`, `POSTED`, `REJECTED`, `CANCELLED`
- `journalEntry`: 자동 생성된 분개전표
- `lines`: 전표 라인

### VoucherLine

- `accountId`, `accountCode`, `accountName`: 선택 시점의 계정과목 정보
- `debitCredit`: `AccountSide.DEBIT` 또는 `AccountSide.CREDIT`
- `supplyAmount`, `vatAmount`, `totalAmount`: 공급가액/VAT/분개 금액
- `description`: 라인 적요

## 자동 분개 로직

### 매출전표

공급가액 1,000,000원, 부가세 100,000원, 수수료 0원 기준:

| 차대 | 계정 |
| --- | --- |
| 차변 | 입금계좌 또는 매출채권 계정 1,100,000 |
| 대변 | 선택한 매출계정 1,000,000 |
| 대변 | 부가세예수금 100,000 |

수수료가 있으면 차변에 `지급수수료(5370)`가 추가되고 입금계좌 금액은 `합계금액 - 수수료`로 계산된다.

### 매입전표

공급가액 1,000,000원, 부가세 100,000원 기준:

| 차대 | 계정 |
| --- | --- |
| 차변 | 선택한 매입/비용계정 1,000,000 |
| 차변 | 부가세대급금 100,000 |
| 대변 | 출금계좌 또는 매입채무 계정 1,100,000 |

부가세 유형이 `ZERO_TAX`, `TAX_FREE`, `EXPORT`, `INVOICE`이면 프론트 자동 계산 VAT는 0으로 처리한다. 백엔드도 같은 기준으로 VAT 자동 계산 시 0으로 처리한다.

프론트 계산 정책:

- 공급가액 입력 후 180ms debounce
- 숫자가 아닌 문자는 제거
- 음수 입력 불가
- 소수점은 허용하지 않고 원 단위 정수로 처리
- 기본 VAT는 `Math.floor(공급가액 * 0.1)`
- 합계금액은 `공급가액 + VAT`
- 사용자가 VAT를 직접 수정하면 합계금액과 자동 분개 미리보기를 다시 계산한다.

## 전표 승인 흐름

1. `DRAFT`: 저장 또는 임시저장 기본 상태
2. `REQUESTED`: 승인요청
3. `APPROVED`: 승인 처리
4. `POSTED`: 게시 처리. 연결된 `JournalEntry.post()`를 호출해 분개전표 상태를 `POSTED`로 변경한다.
5. `REJECTED`, `CANCELLED`: 반려/취소 확장 상태

현재 구현은 상태 전이 순서를 서비스에서 검증한다. `POSTED` 전표는 단순 취소를 막고 향후 수정전표/반제전표로 처리하도록 남겨두었다.

## 회계 처리 흐름

```text
전표 입력 화면
  -> VoucherController
  -> VoucherService
  -> Account 검증
  -> Voucher/VoucherLine 저장
  -> JournalEntry/JournalLine 자동 생성
  -> 차변 합계 = 대변 합계 검증
  -> 승인/게시 상태 변경
```

`JournalEntry.sourceType`은 `VOUCHER`, `sourceId`는 저장된 `Voucher.id`를 사용한다. 향후 `Ledger`, `AccountingPeriod`, `TrialBalance` 집계는 `JournalEntry`와 `JournalLine`의 기존 POSTED 기준 집계 흐름에 연결할 수 있다.

## 프론트 구조

- HTML: `frontend/pages/hr/accounting/voucher_management.html`
- JS: `frontend/assets/js/accounting/voucher_management.js`
- CSS: `frontend/styles/accounting/voucher-management.css`

주요 기능:

- 매출/매입 탭 전환
- VAT 자동 계산 및 직접 수정
- 천단위 콤마 표시
- 거래처 검색 모달
- 계정과목 검색 모달 및 입력창 자동완성
- 계좌 검색 모달 및 입력창 자동완성
- 계정 트리 레벨 기반 들여쓰기 표시
- 자동 분개 미리보기
- 차대 일치 표시
- 저장/임시저장/승인요청 버튼
- 전표 리스트 및 요약 카드
- 데이터가 없을 때 Empty State 표시
- 계정 검색 결과에서 계정코드, 계정명, 계정유형, 사용 상태 표시

## 2026-05-22 고도화 반영

- `VoucherRepository.findListWithLines`를 추가해 전표 목록 조회 시 `VoucherLine`, `JournalEntry`를 함께 조회한다.
- `VoucherService.createVoucher`는 `Voucher` 저장 후 즉시 flush하여 `VoucherLine.id`를 확보하고, `JournalLine.referenceId`에 전표 라인 ID를 연결한다.
- Account Management API에 `/api/v1/accounting/accounts/search`, `/api/accounting/accounts/search`를 추가했다.
- 구형 HR 경로의 `pages/hr/accounting/transactions.html` 삽입 전표 폼도 계정코드 직접 입력 대신 계정 검색 모달로 실제 `accountId`를 선택해 저장하도록 보강했다.

## Validation Rules

프론트:

- 전표일자 필수
- 거래처 필수
- 공급가액은 0보다 커야 함
- 계정과목은 검색 모달 또는 자동완성 결과에서 선택해야 함
- 입금/출금 계좌는 검색 모달 또는 자동완성 결과에서 선택해야 함
- 수수료 음수 불가

백엔드:

- `voucherType`, `voucherDate`, 거래처명 필수
- 자동 분개는 `SALES`, `PURCHASE` 우선 지원
- 매출전표의 사업 계정은 `REVENUE`
- 매입전표의 사업 계정은 `ASSET` 또는 `EXPENSE`
- 입금/출금 계좌는 `ASSET`
- 선택 계정은 말단 계정이고 `allowPosting=true`
- 차변 합계와 대변 합계가 반드시 일치해야 함

## 향후 확장 포인트

- 거래처 유형별 매출채권/매입채무 자동 선택
- 전자세금계산서 ID, 승인번호, 발행상태 연동
- 부가세 신고서 집계 테이블 또는 리포트 연결
- 승인자, 승인일시, 반려사유를 별도 `VoucherApproval`로 분리
- `Ledger` 반영 시점과 회계기간 마감 검증 강화
- 일반전표, 수정전표, 반제전표 탭 활성화

## 재고 ↔ 회계 자동 연동 (INVENTORY 전표)

재고 도메인의 입출고 이벤트는 회계 전표를 자동으로 DRAFT 생성한다. 수동 전표 입력과는 별도의 흐름이다.

### VoucherType.INVENTORY 전표 특성

- `voucherType = INVENTORY`
- `status = DRAFT` (자동 생성 전표는 절대 자동 POSTED 안 함)
- `sourceType`: `VoucherSourceType` Enum (STOCK_INBOUND, STOCK_OUTBOUND, STOCK_ADJUSTMENT_IN, STOCK_ADJUSTMENT_OUT)
- `sourceReferenceId`: 원천 `StockMovement.id`

### 자동 생성 트리거 및 계정 흐름

| 원천 이벤트 | sourceType | 차변 | 대변 |
| :--- | :--- | :--- | :--- |
| 재고 입고 완료 | STOCK_INBOUND | 재고자산(1003) | 외상매입금(2001) |
| 재고 출고 | STOCK_OUTBOUND | 매출원가(5001) | 재고자산(1003) |
| 조정 증가 | STOCK_ADJUSTMENT_IN | 재고자산(1003) | 외상매입금(2001) |
| 조정 감소 | STOCK_ADJUSTMENT_OUT | 재고손실(5003) | 재고자산(1003) |
| 창고 간 이동 | — | (미생성) | (미생성) |

> 창고 간 이동(TRANSFER_IN/OUT)은 회사 내부 자산 이동으로 재고자산 총액에 변화가 없으므로 전표를 생성하지 않는다.

자세한 내용은 `docs/inventory/INVENTORY_ACCOUNTING.md`를 참조한다.

## 테스트 방법

백엔드 컴파일:

```bash
cd backend
./gradlew.bat compileJava
```

수동 확인:

1. `frontend/pages/hr/accounting/voucher_management.html`을 브라우저에서 연다.
2. 매출전표 탭에서 거래처, 매출계정, 입금계좌, 공급가액을 입력한다.
3. 자동 분개 미리보기에서 차변/대변 합계가 일치하는지 확인한다.
4. 임시저장 또는 승인요청을 실행한다.
5. 전표 리스트와 요약 카드가 갱신되는지 확인한다.

검증 완료:

- `./gradlew.bat compileJava` 성공
