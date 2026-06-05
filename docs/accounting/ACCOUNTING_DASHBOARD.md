# Accounting Dashboard

이 문서는 기존 `ACCOUNTING_DASHBOARD_SYSTEM.md`를 `docs/accounting/ACCOUNTING_DASHBOARD.md`로 이동한 문서다.

## 작업 개요

DDUK ERP 회계관리 도메인에 통합 회계 대시보드 시스템을 추가했다. 전표, JournalEntry, JournalLine, Ledger 성격의 분개 집계, AccountingPeriod, Trial Balance, PayrollLedger 데이터를 한 화면에서 조회할 수 있도록 백엔드 API와 프론트 화면을 구성했다.

본 문서는 현재 코드 기반에서 구현된 구조를 설명한다. 별도 Ledger 엔티티는 현재 코드에 존재하지 않으므로, `JournalEntry` + `JournalLine`의 POSTED 분개를 Ledger 집계 원천으로 사용한다.

## 변경 파일 목록

- `backend/src/main/java/com/dduk/domain/accounting/dashboard/api/AccountingDashboardController.java`
- `backend/src/main/java/com/dduk/domain/accounting/dashboard/service/AccountingDashboardService.java`
- `backend/src/main/java/com/dduk/domain/accounting/dashboard/dto/*`
- `backend/src/main/java/com/dduk/repository/accounting/JournalEntryRepository.java`
- `backend/src/main/java/com/dduk/repository/accounting/voucher/VoucherRepository.java`
- `backend/src/main/java/com/dduk/domain/accounting/payroll/repository/PayrollLedgerRepository.java`
- `backend/src/main/java/com/dduk/domain/accounting/period/repository/ClosingLogRepository.java`
- `frontend/pages/hr/accounting/accounting_dashboard.html`
- `frontend/assets/js/accounting/accounting_dashboard.js`
- `frontend/styles/accounting/accounting-dashboard.css`
- `frontend/services/common/sidebar.js`
- `docs/ACCOUNTING_DASHBOARD_SYSTEM.md`

## API 설명

기본 경로는 `/api/v1/accounting/dashboard`이다.

- `GET /api/v1/accounting/dashboard`
  - 통합 대시보드 전체 조회
  - Query: `fiscalYear`, `fiscalMonth`
- `GET /api/v1/accounting/dashboard/kpis`
  - KPI 요약 조회
- `GET /api/v1/accounting/dashboard/trends/profit-loss`
  - 최근 6개월 손익 차트 데이터 조회
- `GET /api/v1/accounting/dashboard/vouchers`
  - 전표 상태 요약 조회
- `GET /api/v1/accounting/dashboard/period`
  - 회계기간 및 월 마감 상태 조회
- `GET /api/v1/accounting/dashboard/alerts`
  - 회계 알림 및 통제 항목 조회
- `GET /api/v1/accounting/dashboard/activities`
  - 최근 회계 활동 조회

응답은 기존 프로젝트 관례에 맞춰 `status`, `data`, `message` 구조를 사용한다.

## KPI 계산 로직

KPI는 `JournalEntry.status = POSTED`인 분개만 대상으로 한다.

- 당월 총 매출: `AccountType.REVENUE` 계정의 대변 합계 - 차변 합계
- 당월 총 비용: `AccountType.EXPENSE` 계정의 차변 합계 - 대변 합계
- 영업이익: 당월 총 매출 - 당월 총 비용
- 당기순이익: 현재 구현에서는 영업이익과 동일한 집계 구조
- 총 자산: `AccountType.ASSET` 계정의 차변 합계 - 대변 합계
- 총 부채: `AccountType.LIABILITY` 계정의 대변 합계 - 차변 합계
- 미승인 전표 수: `DRAFT`, `REQUESTED` 상태 전표 수
- 월 마감 상태: `AccountingPeriod.status`, 기간 미생성 시 `NOT_CREATED`
- 전월 대비 증감률: `(당월 - 전월) / 전월 * 100`

## Dashboard 집계 구조

대시보드 서비스는 다음 기존 시스템을 읽기 전용으로 연결한다.

- `VoucherRepository`: 전표 상태, 오늘 등록 전표, 최근 전표 활동
- `JournalEntryRepository`: Ledger 성격의 POSTED 분개 집계, 월별 손익, 현금 흐름, 최근 분개 활동
- `AccountingPeriodRepository`: 현재 회계기간 및 마감 상태
- `TrialBalanceReportService`: Trial Balance 요약과 주요 계정 잔액
- `PayrollLedgerRepository`: 급여 총액, 계산/미정산 상태, 지급 예정일
- `ClosingLogRepository`: 월 마감 최근 활동

대량 데이터 대응을 위해 화면에서 필요한 요약은 JPA `GROUP BY` 쿼리로 가져오며, 전체 엔티티 로딩 후 애플리케이션에서 집계하는 방식을 피했다.

## 차트 데이터 구조

월별 손익 차트 응답:

```json
{
  "period": "2026-05",
  "revenue": 1580000000,
  "expense": 1120000000,
  "operatingIncome": 460000000,
  "netIncome": 460000000
}
```

자산/부채/자본 구성 차트 응답:

```json
{
  "accountType": "ASSET",
  "label": "자산",
  "amount": 5820000000,
  "ratio": 62.4
}
```

## 회계 처리 흐름

대시보드는 아래 ERP 회계 흐름을 읽기 모델로 반영한다.

1. 전표 입력
2. 승인 또는 반려
3. POSTED 처리
4. JournalEntry 및 JournalLine 생성
5. POSTED JournalLine 기준 Ledger 집계
6. Trial Balance 생성 및 차변/대변 검증
7. AccountingPeriod 기반 월 마감 검증
8. 재무 리포트 및 대시보드 반영

월 마감 상태가 `CLOSED` 또는 `ARCHIVED`이면 프론트 Quick Action에서 입력성 업무를 비활성화할 수 있는 구조를 제공한다.

## 프론트 구조

- HTML: `frontend/pages/hr/accounting/accounting_dashboard.html`
- JS: `frontend/assets/js/accounting/accounting_dashboard.js`
- CSS: `frontend/styles/accounting/accounting-dashboard.css`

주요 기능:

- KPI 카드 렌더링
- Chart.js 기반 월별 손익 차트
- Chart.js 기반 자산/부채/자본 도넛 차트
- 전표 상태 카드
- 현금 흐름, 급여, 월 마감, Trial Balance 요약
- 회계 알림 및 통제 항목
- 최근 회계 활동 리스트
- 빠른 실행 버튼
- 회계연도/월 필터

## 성능 고려사항

- `JournalEntryRepository.aggregatePostedByAccountTypeBetweenDates`
- `JournalEntryRepository.aggregatePostedMonthlyByAccountType`
- `JournalEntryRepository.aggregateCashFlowBetweenDates`
- `VoucherRepository.countStatusByVoucherDateBetween`

위 쿼리는 대시보드용 요약 집계를 DB에서 수행한다. 운영 데이터가 커지면 다음 인덱스를 검토한다.

- `journal_entries(status, transaction_date)`
- `journal_entries(fiscal_year, fiscal_month, status)`
- `journal_items(journal_entry_id, account_id)`
- `vouchers(voucher_date, status)`
- `accounting_payroll_ledgers(payment_year_month, status)`

Dashboard 집계 캐싱은 현재 구현하지 않았지만, 실시간성이 덜 중요한 KPI/차트에는 Caffeine 또는 Redis 캐시를 붙일 수 있다.

## 향후 확장 포인트

- 별도 `Ledger` 엔티티가 도입되면 JournalLine 집계 쿼리를 Ledger 집계 쿼리로 교체
- 부가세 신고 대상 알림을 VatType과 세금계정 기준으로 구체화
- 재고 음수 알림을 대시보드 API에 직접 연동
- 경영진용 월간 재무 리포트 카드 추가
- 실시간 갱신이 필요하면 SSE 또는 polling 주기 설정 추가
- KPI 산식에 영업외수익, 영업외비용, 법인세 계정을 반영

## 테스트 방법

백엔드 컴파일:

```bash
cd backend
./gradlew compileJava
```

프론트 확인:

1. Spring Boot 서버 실행
2. 브라우저에서 `frontend/pages/hr/accounting/accounting_dashboard.html` 열기
3. 회계연도/월 변경 후 KPI, 차트, 전표, 월 마감, 급여, 알림 영역 렌더링 확인
4. `/api/v1/accounting/dashboard?fiscalYear=2026&fiscalMonth=5` 응답 구조 확인
