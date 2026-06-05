# Accounting Enhancement 2026-05-22

## 작업 개요

DDUK ERP 회계관리 화면과 API를 실제 DB 저장/조회 흐름 기준으로 보강했다. 기존 회계 문서는 `docs/accounting/` 하위로 분리하고, 루트에는 하네스와 공통 규칙 문서를 유지했다.

## 변경 파일 목록

- `backend/src/main/java/com/dduk/controller/accounting/AccountManagementController.java`
- `backend/src/main/java/com/dduk/service/accounting/AccountManagementService.java`
- `backend/src/main/java/com/dduk/repository/accounting/voucher/VoucherRepository.java`
- `backend/src/main/java/com/dduk/service/accounting/voucher/VoucherService.java`
- `backend/src/main/java/com/dduk/domain/accounting/period/service/MonthlyClosingService.java`
- `frontend/assets/js/accounting/voucher_management.js`
- `frontend/styles/accounting/voucher-management.css`
- `frontend/services/hr/hr-backend-api.js`
- `frontend/services/hr/hr-backend-pages.js`
- `frontend/styles/hr/transactions.css`

## 신규 생성 파일 목록

- `docs/accounting/README.md`
- `docs/accounting/ACCOUNTING_ENHANCEMENT_2026-05-22.md`
- `docs/common/README.md`
- `docs/inventory/README.md`
- `docs/ui/README.md`

## 문서 이동 및 네이밍 정리

| 기존 문서 | 변경 후 |
| --- | --- |
| `docs/ACCOUNTING_VOUCHER_MANAGEMENT.md` | `docs/accounting/ACCOUNTING_TRANSACTION.md` |
| `docs/ACCOUNTING_DASHBOARD_SYSTEM.md` | `docs/accounting/ACCOUNTING_DASHBOARD.md` |
| `docs/ACCOUNTING_REPORTS_SYSTEM.md` | `docs/accounting/ACCOUNTING_REPORT.md` |
| `docs/ACCOUNTING_MONTHLY_CLOSING.md` | `docs/accounting/ACCOUNTING_MONTH_END.md` |
| `docs/ACCOUNTING_TRIAL_BALANCE.md` | `docs/accounting/ACCOUNTING_TRIAL_BALANCE.md` |
| `docs/ACCOUNTING_PAYROLL_MANAGEMENT.md` | `docs/accounting/ACCOUNTING_PAYROLL.md` |
| `docs/CHART_OF_ACCOUNTS.md` | `docs/accounting/CHART_OF_ACCOUNTS.md` |

## API 목록

| Method | Path | 설명 |
| --- | --- | --- |
| `GET` | `/api/accounting/accounts/search?keyword=현금` | 계정과목 검색 |
| `GET` | `/api/v1/accounting/accounts/search?keyword=현금` | 계정과목 검색 v1 경로 |
| `GET` | `/api/accounting/vouchers?type=SALES` | 전표 목록 조회 |
| `GET` | `/api/accounting/vouchers/summary` | 전표 요약 조회 |
| `POST` | `/api/accounting/vouchers` | 전표 및 분개 저장 |
| `PATCH` | `/api/accounting/vouchers/{id}/status?status=REQUESTED` | 전표 상태 변경 |
| `GET` | `/api/v1/accounting/monthly-closing/summary` | 월 마감 요약 조회 |

## DB 반영 사항

- 신규 테이블 또는 컬럼은 추가하지 않았다.
- `Voucher` 저장 후 즉시 flush하여 `VoucherLine.id`를 확정한 뒤 `JournalLine.referenceId`에 연결한다.
- `Voucher` 목록 조회는 `VoucherLine`, `JournalEntry`를 fetch join하여 새로고침 후에도 거래내역이 유지되도록 보강했다.

## 구현 완료 기능 요약

- 전표 저장 후 DB 저장, 하단 리스트 재조회, 새로고침 유지 흐름 보강
- 계정과목 검색 API를 Account Management 경로에 추가
- 전표 화면의 계정 검색 결과에 계정코드, 계정명, 계정유형, 사용 상태 표시
- 데이터 없음/조회 중 상태 표시 추가
- 월 마감 요약은 회계기간 미생성 시에도 현재 기간, OPEN 기본 상태, 0원 값을 반환
- 구형 HR 전표 입력 경로도 계정 ID 기반 저장 payload로 보강

## 남은 TODO

- 일반전표, 수정전표, 반제전표 탭 활성화
- 승인자/승인일시/반려사유 이력 분리
- 부가세 신고 확정 검증과 세금계산서 외부 연동
- 프론트 문구 인코딩 정리 및 화면별 한국어 라벨 재검수
- Playwright 기반 주요 회계 화면 회귀 테스트 추가

## 테스트 방법

```bash
cd backend
./gradlew.bat compileJava
```

브라우저 확인:

1. `frontend/pages/hr/accounting/voucher_management.html`에서 계정 검색 후 전표 저장
2. 하단 전표 목록에 저장 건이 즉시 표시되는지 확인
3. 새로고침 후 저장 건 유지 확인
4. `/api/accounting/accounts/search?keyword=현금` 응답 확인
5. `frontend/pages/hr/accounting/monthly_closing.html`에서 회계기간 미생성 월도 기본 요약이 표시되는지 확인

