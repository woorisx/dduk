# Accounting Month End

이 문서는 기존 `ACCOUNTING_MONTHLY_CLOSING.md`를 `docs/accounting/ACCOUNTING_MONTH_END.md`로 이동한 문서다.

## 작업 개요

DDUK ERP 회계관리 도메인의 월 마감 기능을 회계기간(AccountingPeriod) 중심의 ERP 스타일 마감 통제 구조로 확장했다. 마감 전 검증, 전표/분개/급여 잠금, 재오픈, 마감 로그, 향후 결산 자동분개 및 재무제표 연계를 고려한 구조를 포함한다.

## 변경 파일 목록

- `backend/src/main/java/com/dduk/domain/accounting/period/entity/*`
- `backend/src/main/java/com/dduk/domain/accounting/period/dto/*`
- `backend/src/main/java/com/dduk/domain/accounting/period/repository/*`
- `backend/src/main/java/com/dduk/domain/accounting/period/service/MonthlyClosingService.java`
- `backend/src/main/java/com/dduk/domain/accounting/period/api/MonthlyClosingController.java`
- `backend/src/main/java/com/dduk/service/accounting/JournalValidationService.java`
- `backend/src/main/java/com/dduk/service/accounting/voucher/VoucherService.java`
- `backend/src/main/java/com/dduk/domain/accounting/payroll/service/PayrollManagementService.java`
- `backend/src/main/java/com/dduk/repository/accounting/JournalEntryRepository.java`
- `backend/src/main/java/com/dduk/repository/accounting/voucher/VoucherRepository.java`
- `backend/src/main/java/com/dduk/domain/accounting/payroll/repository/PayrollLedgerRepository.java`
- `backend/src/main/java/com/dduk/repository/inventory/InventoryRepository.java`
- `frontend/pages/hr/accounting/monthly_closing.html`
- `frontend/assets/js/accounting/monthly_closing.js`
- `frontend/styles/accounting/monthly-closing.css`

## API 설명

기본 경로: `/api/v1/accounting/monthly-closing`

- `GET /summary?fiscalYear=&fiscalMonth=`: 현재 또는 선택 기간 요약 조회
- `GET /periods?fiscalYear=`: 회계기간 목록 조회
- `GET /periods/{fiscalYear}/{fiscalMonth}`: 회계기간 상태 조회
- `POST /periods`: 회계기간 생성
- `POST /periods/year`: 1년치 월별 회계기간 자동 생성
- `POST /periods/{fiscalYear}/{fiscalMonth}/validate`: 마감 사전 검증
- `POST /periods/{fiscalYear}/{fiscalMonth}/close`: 월 마감 실행
- `POST /periods/{fiscalYear}/{fiscalMonth}/reopen`: 마감 재오픈
- `GET /periods/{periodId}/logs`: 마감 로그 조회

## Entity 구조

`AccountingPeriod`

- `fiscalYear`, `fiscalMonth`
- `startDate`, `endDate`
- `status`: `OPEN`, `PRE_CLOSING`, `CLOSED`, `REOPENED`, `ARCHIVED`
- `closedAt`, `closedBy`
- `reopenedAt`, `reopenedBy`, `reopenCount`

`ClosingLog`

- `accountingPeriod`
- `actionType`: 생성, 검증 성공/실패, PRE_CLOSING, 마감, 재오픈 등
- `fromStatus`, `toStatus`
- `actor`, `actionAt`, `ipAddress`, `message`

## 회계기간 흐름

1. 회계기간 생성 또는 연간 자동 생성
2. 거래/전표 입력
3. 전표 승인 및 게시
4. 원장 반영
5. 마감 검증
6. `OPEN` 또는 `REOPENED` -> `PRE_CLOSING`
7. 전표, JournalEntry, Ledger 성격 데이터 잠금
8. `CLOSED`
9. 관리자 권한 흐름에서 `REOPENED` 가능

## 월 마감 프로세스

마감 실행 API는 내부적으로 검증 결과를 다시 계산한다. `ERROR`가 있으면 기본적으로 마감을 차단한다. 검증 통과 후 상태를 `PRE_CLOSING`으로 전환하고 로그를 남긴 뒤 `CLOSED`로 전환한다. 자동 마감 분개는 아직 생성하지 않고, 감가상각/선급/미지급/재고평가/수익비용 마감 분개를 붙일 수 있는 서비스 경계만 유지했다.

## 검증 로직 설명

- 전표 검증: `DRAFT`, `REQUESTED`, 미게시 전표 수 확인
- 분개 검증: 차변/대변 불일치, 라인 누락, 마이너스 금액 확인
- 계정 검증: 기간 내 분개 라인에 사용된 비활성 계정, posting 차단 계정, 비말단 계정 확인
- 급여 검증: 지급월 기준 미확정 급여대장 확인
- 재고 검증: 음수 재고 확인
- 세금 검증: 부가세 도메인 연결 전까지 `WARNING` 확장 지점으로 표시

## 잠금 처리 설명

`CLOSED` 또는 `ARCHIVED` 기간은 `MonthlyClosingService.assertPeriodMutable(date)`로 차단한다. 현재 연결된 경로는 다음과 같다.

- 전표 생성 및 상태 변경
- JournalEntry 생성, 게시, 취소, 삭제
- PayrollLedger 생성, 계산, 확정

Ledger는 별도 물리 Entity가 없고 현재 JournalEntry 기반 원장 조회 구조이므로 JournalEntry 잠금으로 통제한다.

## 회계 처리 흐름

전표는 Voucher -> JournalEntry -> JournalLine으로 이어진다. 마감 요약과 검증은 회계기간의 `fiscalYear`, `fiscalMonth`, `startDate`, `endDate`를 기준으로 전표 수, 차변/대변 합계, 미게시 상태를 집계한다. 향후 TrialBalance, 재무제표, 부가세 신고는 동일 기간 키를 기준으로 연계한다.

## 프론트 구조

- 페이지: `frontend/pages/hr/accounting/monthly_closing.html`
- JS: `frontend/assets/js/accounting/monthly_closing.js`
- CSS: `frontend/styles/accounting/monthly-closing.css`

화면은 요약 카드, 회계기간 목록 테이블, 회계기간 생성 모달, 검증 결과 모달, 마감 로그 모달로 구성된다. 상태별로 마감/재오픈 버튼을 제어한다.

## 향후 확장 포인트

- 관리자 권한 기반 재오픈 승인
- 자동 결산 분개 생성
- 감가상각, 선급/미지급, 재고평가, 원가대체 분개
- 재무제표 생성 상태 관리
- 부가세 확정 및 신고 대상 검증
- 기간별 Ledger 물리 잠금 Entity 도입
- 감사 로그 화면 메뉴 분리

## 테스트 방법

백엔드 컴파일:

```bash
cd backend
./gradlew.bat compileJava
```

프론트 확인:

```text
frontend/pages/hr/accounting/monthly_closing.html
```

주요 수동 확인:

- 회계기간 생성 시 필수값과 시작일/종료일 검증
- 중복 회계기간 생성 차단
- 마감 검증 결과 목록 표시
- 검증 오류가 있는 기간의 마감 차단
- `CLOSED` 기간의 전표/분개/급여 변경 차단
- 재오픈 시 상태와 로그 기록 확인
