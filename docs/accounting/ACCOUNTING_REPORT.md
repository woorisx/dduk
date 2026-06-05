# Accounting Report

이 문서는 기존 `ACCOUNTING_REPORTS_SYSTEM.md`를 `docs/accounting/ACCOUNTING_REPORT.md`로 이동한 문서다.

## 작업 개요

DDUK ERP 회계관리 도메인에 ERP 스타일 회계 리포트 및 재무 분석 시스템을 추가했다. 기존 단순 리포트 화면을 대체할 수 있도록 KPI, 월별 손익 추이, 자산/부채/자본 구성, 매출 분석, 비용 분석, 계정별 잔액 분석, 월별 전표 흐름, 급여 비용 분석, Excel/PDF 출력 구조를 구현했다.

현재 코드에 별도 `Ledger` 엔티티는 존재하지 않으므로, 본 시스템은 `POSTED JournalEntry + JournalLine`을 Ledger 집계 원천으로 사용한다.

## 변경 파일 목록

- `backend/src/main/java/com/dduk/domain/accounting/report/api/AccountingReportAnalyticsController.java`
- `backend/src/main/java/com/dduk/domain/accounting/report/service/AccountingReportAnalyticsService.java`
- `backend/src/main/java/com/dduk/domain/accounting/report/dto/analytics/*`
- `backend/src/main/java/com/dduk/repository/accounting/voucher/VoucherRepository.java`
- `backend/src/main/java/com/dduk/domain/accounting/payroll/repository/PayrollLedgerRepository.java`
- `frontend/pages/hr/accounting/accounting_reports.html`
- `frontend/assets/js/accounting/accounting_reports.js`
- `frontend/styles/accounting/accounting-reports.css`
- `frontend/services/common/sidebar.js`
- `frontend/assets/js/accounting/accounting_dashboard.js`
- `docs/ACCOUNTING_REPORTS_SYSTEM.md`

## API 설명

기본 경로는 `/api/v1/accounting/reports/analytics`이다.

- `GET /api/v1/accounting/reports/analytics`
  - 통합 회계 리포트 조회
  - Query: `startDate`, `endDate`, `reportBasis`, `reportType`
- `GET /api/v1/accounting/reports/analytics/kpis`
  - KPI 요약 조회
- `GET /api/v1/accounting/reports/analytics/trends/profit-loss`
  - 월별 손익 추이 조회
- `GET /api/v1/accounting/reports/analytics/balance-composition`
  - 자산/부채/자본 구성 조회
- `GET /api/v1/accounting/reports/analytics/accounts`
  - 계정별 잔액 분석 조회
- `GET /api/v1/accounting/reports/analytics/export/excel`
  - CSV 기반 Excel 다운로드
- `GET /api/v1/accounting/reports/analytics/export/pdf`
  - PDF 다운로드

`reportBasis` 값:

- `MONTHLY`
- `QUARTERLY`
- `HALF_YEARLY`
- `YEARLY`

`reportType` 값:

- `COMPREHENSIVE`
- `SALES`
- `EXPENSE`
- `PROFIT_LOSS`
- `ASSET`
- `LIABILITY`
- `ACCOUNT`
- `PAYROLL`

## KPI 계산 로직

KPI는 Trial Balance와 POSTED 분개 기준으로 산출한다.

- 총 매출: `AccountType.REVENUE` 계정의 정상잔액 기준 기말잔액 합계
- 총 비용: `AccountType.EXPENSE` 계정의 정상잔액 기준 기말잔액 합계
- 영업이익: 총 매출 - 총 비용
- 당기순이익: 현재 구현에서는 영업이익과 동일
- 총 자산: `AccountType.ASSET` 계정 기말잔액 합계
- 총 부채: `AccountType.LIABILITY` 계정 기말잔액 합계
- 총 자본: `AccountType.EQUITY` 계정 기말잔액 합계
- 부채비율: 총부채 / 총자본 * 100
- 유동비율: 현재 계정 세분류가 없으므로 총자산 / 총부채 * 100으로 대체 계산한다. 유동/비유동 계정 분류가 도입되면 유동자산/유동부채 기준으로 교체한다.
- 전기간 대비 증감률: 동일 일수 직전 기간과 비교한다.

## 집계 로직 설명

주요 집계는 DB `GROUP BY` 쿼리와 Trial Balance 서비스를 조합한다.

- 계정별 잔액: `TrialBalanceReportService`
- 월별 손익: `JournalEntryRepository.aggregatePostedMonthlyByAccountType`
- 전기간 유형별 합계: `JournalEntryRepository.aggregatePostedByAccountTypeBetweenDates`
- 매출 분석: `VoucherRepository.aggregateVendorAmounts`
- 월별 전표 흐름: `VoucherRepository.countMonthlyStatusByVoucherDateBetween`
- 급여 분석: `PayrollLedgerRepository.sumPayrollAmountsBetweenYearMonth`, `aggregateDepartmentPayroll`

## 차트 데이터 구조 설명

월별 손익 추이:

```json
{
  "period": "2026-05",
  "revenue": 1580000000,
  "expense": 1120000000,
  "operatingIncome": 460000000,
  "netIncome": 460000000
}
```

자산/부채/자본 구성:

```json
{
  "accountType": "ASSET",
  "label": "자산",
  "amount": 5820000000,
  "ratio": 62.4
}
```

월별 전표 흐름:

```json
{
  "period": "2026-05",
  "totalCount": 42,
  "approvedCount": 12,
  "postedCount": 27,
  "cancelledCount": 3
}
```

## 계정 구조 설명

계정 구조는 `Account`의 계층 구조를 유지한다.

- `parentAccountId`로 상위 계정 연결
- `level`로 계층 깊이 표시
- `leaf`로 말단 계정 여부 표시
- `AccountType`으로 재무제표 섹션 분류
- `AccountSide`로 정상잔액 방향 처리

자산/비용은 차변 증가, 부채/자본/수익은 대변 증가로 계산한다.

## 회계 처리 흐름

1. 전표 입력
2. 전표 승인
3. POSTED 처리
4. JournalEntry 및 JournalLine 생성
5. POSTED JournalLine을 Ledger 성격의 원천 데이터로 집계
6. Trial Balance 생성
7. 재무상태표, 손익계산서, 계정별 분석으로 확장
8. 회계 리포트 및 경영 분석 화면 반영

## 프론트 구조

- HTML: `frontend/pages/hr/accounting/accounting_reports.html`
- JS: `frontend/assets/js/accounting/accounting_reports.js`
- CSS: `frontend/styles/accounting/accounting-reports.css`

주요 기능:

- 시작일/종료일 필터
- 조회 기준 선택
- 회계기간 선택
- 리포트 유형 선택
- KPI 카드 렌더링
- Chart.js 기반 손익 추이 및 구성 차트
- 매출/비용/계정/전표/급여 분석 테이블
- Excel 다운로드
- PDF 다운로드
- 브라우저 인쇄

## 성능 고려사항

대량 JournalLine, VoucherLine, PayrollLedger 데이터에 대비하여 화면 단위 집계는 DB에서 수행한다.

운영 환경에서는 다음 인덱스를 검토한다.

- `journal_entries(status, transaction_date)`
- `journal_entries(fiscal_year, fiscal_month, status)`
- `journal_items(journal_entry_id, account_id)`
- `vouchers(voucher_date, voucher_type, status)`
- `voucher_lines(voucher_id, account_id)`
- `accounting_payroll_ledgers(payment_year_month, status)`
- `accounting_payroll_ledger_employees(ledger_id)`

경영 리포트처럼 실시간성이 낮은 데이터는 기간 단위 캐싱을 적용할 수 있다.

## 향후 확장 포인트

- 별도 Ledger 엔티티 도입 시 집계 원천 교체
- 재무상태표(B/S), 손익계산서(P/L), 현금흐름표(C/F) 전용 페이지 분리
- 제조원가명세서, 부가세 신고 리포트 추가
- 유동/비유동 계정 속성 도입 후 유동비율 정교화
- 사업부별 손익, 예산 대비 실적, Forecast 분석
- CEO Dashboard 및 경영 KPI 분석 화면 연계

## 테스트 방법

백엔드 컴파일:

```bash
cd backend
./gradlew compileJava
```

API 확인:

```bash
GET /api/v1/accounting/reports/analytics?startDate=2026-05-01&endDate=2026-05-31&reportBasis=MONTHLY&reportType=COMPREHENSIVE
```

프론트 확인:

1. Spring Boot 서버 실행
2. `frontend/pages/hr/accounting/accounting_reports.html` 접속
3. 기간 조회, KPI 카드, 차트, 테이블 렌더링 확인
4. Excel/PDF 버튼 다운로드 확인
5. 인쇄 버튼 동작 확인
