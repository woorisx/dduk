# Accounting Payroll

이 문서는 기존 `ACCOUNTING_PAYROLL_MANAGEMENT.md`를 `docs/accounting/ACCOUNTING_PAYROLL.md`로 이동한 문서다.

## 작업 개요

회계관리 도메인의 기존 단순 급여 계산 흐름과 별도로, ERP 급여대장 중심의 급여관리 구조를 추가했다.

핵심 흐름은 다음과 같다.

1. 급여대장 생성
2. 대상 사원 및 정산항목 확정
3. 지급항목/공제항목 계산
4. 급여대장 상세 및 급여명세서 조회
5. Account 기반 자동 분개 초안 생성
6. 급여대장 확정

현재 구현은 실무 ERP 구조의 초안이며, 4대보험/원천세/연말정산은 계산 모듈을 교체하거나 정산항목을 확장할 수 있도록 Enum 및 항목 엔티티로 분리했다.

## 변경 파일 목록

- `backend/src/main/java/com/dduk/domain/accounting/payroll/entity/*`
- `backend/src/main/java/com/dduk/domain/accounting/payroll/dto/*`
- `backend/src/main/java/com/dduk/domain/accounting/payroll/repository/PayrollLedgerRepository.java`
- `backend/src/main/java/com/dduk/domain/accounting/payroll/service/PayrollManagementService.java`
- `backend/src/main/java/com/dduk/domain/accounting/payroll/api/PayrollManagementController.java`
- `backend/src/main/java/com/dduk/repository/hr/EmployeeRepository.java`
- `backend/src/main/java/com/dduk/repository/hr/PayrollContractRepository.java`
- `frontend/pages/hr/accounting/payroll_management.html`
- `frontend/styles/accounting/payroll-management.css`
- `frontend/assets/js/accounting/payroll_management.js`
- `docs/ACCOUNTING_PAYROLL_MANAGEMENT.md`

## API 설명

Base URL: `/api/v1/accounting/payroll-ledgers`

| Method | Path | 설명 |
| --- | --- | --- |
| `GET` | `/summary` | 급여대장 요약 카드 데이터 조회 |
| `GET` | `` | 최근 급여대장 목록 조회 |
| `POST` | `` | 급여대장 생성 |
| `POST` | `/calculate` | 급여대장 생성 후 즉시 계산 |
| `GET` | `/{ledgerId}` | 급여대장 상세 조회 |
| `POST` | `/{ledgerId}/calculate` | 기존 급여대장 계산 실행 |
| `POST` | `/{ledgerId}/confirm` | 계산 완료 급여대장 확정 |
| `GET` | `/{ledgerId}/payslips` | 사원별 급여명세서 조회 |
| `GET` | `/employees/search?keyword=` | 급여 대상 사원 검색 |

생성 요청 주요 필드:

- `attributionYearMonth`: 귀속연월, `YYYY-MM`
- `payrollType`: `SALARY`, `BONUS`, `INCENTIVE`, `SEVERANCE`, `OTHER_ALLOWANCE`
- `taxType`: `TAXABLE`, `NON_TAXABLE`, `MIXED`
- `settlementCycle`: `MONTHLY`, `QUARTERLY`, `HALF_YEARLY`, `YEARLY`
- `targetPeriodMode`: `BULK`, `ITEM`
- `paymentDate`: 지급일
- `paymentYearMonth`: 지급연월, `YYYY-MM`
- `ledgerName`: 급여대장명칭
- `settlementItemSelectionMode`: `ALL`, `SELECTED`
- `settlementItems`: 선택 정산항목
- `employeeSelectionMode`: `ALL`, `SELECTED`
- `employeeIds`: 선택 사원 ID 목록

## Entity 구조

- `PayrollLedger`
  - 급여대장 Header
  - 귀속연월, 급여구분, 세금구분, 정산기간, 지급일, 상태, 합계금액, JournalEntry 연결을 가진다.
- `PayrollLedgerEmployee`
  - 급여대장별 사원 계산 Row
  - 사원 스냅샷, 지급총액, 공제총액, 실지급액, 지급상태를 가진다.
- `PayrollItem`
  - 지급항목
  - 기본급, 식대, 직책수당, 연장수당, 상여금, 성과급을 분리한다.
- `PayrollDeductionItem`
  - 공제항목
  - 국민연금, 건강보험, 장기요양보험, 고용보험, 소득세, 지방소득세를 분리한다.
- `PayrollSettlementItem`
  - 정산항목
  - 연말정산, 건강보험정산, 장기요양보험정산, 국민연금정산, 고용보험정산을 분리한다.

상태값:

- `DRAFT`
- `READY`
- `CALCULATED`
- `CONFIRMED`
- `POSTED`
- `CLOSED`
- `CANCELLED`

## 급여 계산 흐름

1. 대상 사원을 조회한다.
   - 전체 선택 시 퇴직 상태가 아닌 사원을 대상으로 한다.
   - 선택 모드에서는 생성 요청의 `employeeIds`를 급여대장에 보존한다.
2. 사원별 급여계약(`PayrollContract`)을 조회한다.
   - `findByEmployeeIdIn`으로 일괄 조회해 N+1을 줄인다.
3. 지급항목을 계산한다.
   - 기본급: 계약 기본급
   - 식대: 세금구분에 따라 과세/비과세 속성 부여
   - 직책수당: 기본급의 5%
   - 상여/성과급: 급여구분과 `bonusRateOrAmount` 기준
4. 공제항목을 계산한다.
   - 국민연금 4.5%
   - 건강보험 3.545%
   - 장기요양보험: 건강보험의 12.95%
   - 고용보험 0.9%
   - 소득세: 간이 누진식 초안
   - 지방소득세: 소득세의 10%
5. 사원별 지급총액, 공제총액, 실지급액을 검증한다.
6. 급여대장 합계와 상태를 `CALCULATED`로 갱신한다.

현재 세율/공식은 초안이다. 실제 적용 전 법정 요율과 원천징수 간이세액표 기반 계산기로 교체해야 한다.

## 자동 분개 로직

급여계산 완료 시 `JournalEntry` 초안을 생성한다.

차변:

- 급여/상여 지급총액: `PayrollAccountingRole.PAYROLL_EXPENSE`

대변:

- 4대보험 및 원천세 예수금: `PayrollAccountingRole.WITHHOLDING_PAYABLE`
- 미지급급여: `PayrollAccountingRole.SALARY_PAYABLE`

`PayrollAccountingRole`은 기존 `AccountingConstants`의 계정 코드를 통해 Account를 조회한다. 서비스 로직은 문자열 계정명을 직접 사용하지 않고, Account 엔티티의 전기 가능 말단 계정 여부를 검증한다.

## 회계 처리 흐름

현재 흐름:

1. 급여대장 계산
2. `AccountRepository.findByCode`로 계정과목 조회
3. `JournalEntry` 생성
4. `JournalLine` 차변/대변 생성
5. 차변/대변 합계 검증
6. 급여대장에 `journalEntry` 연결

향후 흐름:

- 확정 후 전표(`Voucher`) 생성
- 전표 승인 후 JournalEntry POSTED 처리
- Ledger/General Ledger 반영
- 지급 완료 시 보통예금 대체 분개 생성

## 프론트 구조

페이지:

- `frontend/pages/hr/accounting/payroll_management.html`

JS:

- `frontend/assets/js/accounting/payroll_management.js`

CSS:

- `frontend/styles/accounting/payroll-management.css`

주요 UI:

- 상단 요약 카드
- 급여정보입력 모달
- 대상항목 선택
- 대상사원 검색 모달
- 급여대장 리스트
- 급여대장 상세 모달
- 급여명세서 모달

## 향후 확장 포인트

- 4대보험 요율 Master Table
- 원천세 간이세액표 기반 계산 모듈
- 연말정산 정산항목 상세 테이블
- 부서/직급/고용형태별 대상자 선택
- 은행 계좌 검증 API
- PDF 급여명세서 출력
- 이메일 발송
- 급여 확정 후 Voucher 생성 및 승인 워크플로우
- 지급 완료 시 보통예금 대체 분개 생성

## 테스트 방법

백엔드 컴파일:

```bash
cd backend
./gradlew.bat compileJava
```

수동 확인:

1. `frontend/pages/hr/accounting/payroll_management.html`을 연다.
2. 급여정보입력 모달에서 필수값을 입력한다.
3. 대상사원을 전체 또는 선택으로 지정한다.
4. 저장 또는 계산실행을 누른다.
5. 리스트에서 급여대장 상세와 급여명세서를 조회한다.
6. 계산 완료 건은 급여확정을 실행한다.

주의:

- 계산 실행에는 대상 사원의 `PayrollContract`가 필요하다.
- 자동 분개에는 기존 계정과목 관리의 급여비용, 예수금, 미지급급여 계정이 필요하다.
