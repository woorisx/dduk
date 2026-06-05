# Accounting Trial Balance

## 작업 개요

DDUK ERP 회계관리의 합계잔액시산표를 ERP 스타일 재무 리포트 구조로 확장했다. 기존 단순 계정별 합계 조회를 기간 기준 기초잔액, 당기합계, 기말잔액 계산 구조로 리팩토링하고, Account 계층 구조와 AccountSide 방향을 반영해 재무상태표(B/S), 손익계산서(P/L)로 변환 가능한 기반을 추가했다.

## 변경 파일 목록

- `backend/src/main/java/com/dduk/domain/accounting/report/api/TrialBalanceReportController.java`
- `backend/src/main/java/com/dduk/domain/accounting/report/service/TrialBalanceReportService.java`
- `backend/src/main/java/com/dduk/domain/accounting/report/service/FinancialStatementMapper.java`
- `backend/src/main/java/com/dduk/domain/accounting/report/dto/*`
- `backend/src/main/java/com/dduk/repository/accounting/JournalEntryRepository.java`
- `backend/src/main/java/com/dduk/controller/accounting/AccountingController.java`
- `frontend/pages/hr/accounting/trial_balance.html`
- `frontend/assets/js/accounting/trial_balance.js`
- `frontend/styles/accounting/trial-balance.css`

## API 설명

기본 경로: `/api/v1/accounting/reports`

- `GET /trial-balance`: 합계잔액시산표 조회
- `GET /trial-balance/aggregation`: 기간별 집계 요약 조회
- `GET /accounts/tree`: 계정 계층 조회
- `GET /trial-balance/export`: CSV 기반 Excel 다운로드
- `GET /financial-statements/{statementType}`: 재무제표 변환

`statementType` 예시:

- `BALANCE_SHEET`
- `PROFIT_LOSS`
- `MANUFACTURING_COST`
- `CASH_FLOW`
- `VAT_REPORT`

## 집계 로직 설명

집계는 `JournalEntry(status = POSTED)`와 `JournalLine`을 기준으로 한다.

- 기초 집계: `transactionDate < startDate`
- 당기 집계: `startDate <= transactionDate <= endDate`
- 계정별 집계: `JournalLine.account.id` 기준 `GROUP BY`
- 계층 롤업: leaf 계정 집계 금액을 부모 계정으로 누적

대량 데이터 처리를 위해 Java에서 전체 라인을 순회하지 않고 DB `SUM/GROUP BY` 결과만 받아 계정 트리에 반영한다.

## 계정 계층 구조 설명

`Account.parentAccount`, `Account.children`, `Account.level`, `Account.sortOrder`를 사용한다.

화면 필터:

- `ALL`: 전체
- `MAJOR`: 대분류
- `MIDDLE`: 중분류까지
- `SMALL`: 소분류까지
- `ACCOUNT`: leaf 계정과목

`summaryOnly` 옵션은 leaf 계정을 제외하고 상위 합계 계정 중심으로 표시한다.

## 계산 공식 설명

기초잔액:

```text
기초잔액 = 시작일 이전 POSTED 분개 누적 잔액
```

당기합계:

```text
당기 차변합계 = 기간 내 JournalLine.debitAmount 합계
당기 대변합계 = 기간 내 JournalLine.creditAmount 합계
```

기말잔액:

```text
기말잔액 = 기초잔액 + 당기 순증감
```

잔액 표시는 계정 방향에 따라 차변/대변 컬럼 중 한쪽에 표시한다.

## 계정 방향 처리 설명

`Account.normalBalance`를 기준으로 계산한다.

- `ASSET`, `EXPENSE`: 일반적으로 차변 증가
- `LIABILITY`, `EQUITY`, `REVENUE`: 일반적으로 대변 증가

구현은 `Account.normalBalance` 필드를 우선 사용한다. 차변 정상 계정은 `debit - credit`, 대변 정상 계정은 `credit - debit`을 잔액으로 계산한다. 잔액이 음수이면 반대편 컬럼에 표시한다.

## 회계 처리 흐름

1. Voucher 또는 Payroll 등 업무 데이터에서 JournalEntry 생성
2. JournalLine에 계정, 차변, 대변 금액 기록
3. POSTED 상태의 JournalEntry만 리포트 집계 대상
4. 기간 조건으로 기초/당기 집계
5. Account 계층 구조로 상위 계정 롤업
6. Trial Balance 생성
7. B/S, P/L 변환 구조로 전달

월 마감(AccountingPeriod)은 조회 기간 기준 리포트와 연결 가능하도록 날짜 기반으로 구성했다. CLOSED 기간도 조회 가능하며, 수정 통제는 월 마감 시스템에서 처리한다.

## 프론트 구조

- 페이지: `frontend/pages/hr/accounting/trial_balance.html`
- JS: `frontend/assets/js/accounting/trial_balance.js`
- CSS: `frontend/styles/accounting/trial-balance.css`

주요 기능:

- 기간 검색
- 조회 기준 선택
- 계정 레벨 필터
- 잔액 없는 계정 포함
- 보조계정 포함
- 합계만 보기
- 손익계산서 형식 보기
- 계층형 테이블 Expand/Collapse
- Excel 다운로드
- 인쇄

## 성능 고려사항

- `JournalLine` 전체 조회를 피하고 DB 집계 쿼리 사용
- 기초/당기 집계를 각각 `GROUP BY account.id`로 수행
- 계정 트리는 한 번 조회 후 메모리에서 롤업
- DTO 응답으로 Lazy Loading 노출 최소화
- 향후 Ledger 물리 테이블이 추가되면 동일 DTO를 유지하고 집계 소스만 교체 가능

## 향후 확장 포인트

- Ledger 전용 집계 테이블 기반 리포트
- 재무상태표 계정 배열 규칙 고도화
- 손익계산서 매출총이익/영업이익/법인세비용 구조 세분화
- 제조원가명세서
- 현금흐름표
- 부가세 신고서
- Excel XLSX 전용 라이브러리 기반 서식 다운로드
- AccountingPeriod 기준 마감 완료 기간 검증 표시

## 테스트 방법

백엔드 컴파일:

```bash
cd backend
./gradlew.bat compileJava
```

프론트 JS 문법 확인:

```bash
node --check frontend/assets/js/accounting/trial_balance.js
```

브라우저 확인:

```text
frontend/pages/hr/accounting/trial_balance.html
```

주요 수동 확인:

- 시작일/종료일 필수값 검증
- 종료일이 시작일보다 빠른 경우 차단
- 계정 레벨 필터별 행 표시
- Expand/Collapse 동작
- 당기 차변/대변 합계 일치 여부 표시
- CSV 다운로드
- 손익계산서 형식 보기에서 수익/비용 계정만 표시
