# DDUK ERP 표준 계정과목(Chart of Accounts) 명세서

이 문서는 DDUK ERP의 계정과목(Chart of Accounts) 구조 및 Seed 데이터 명세, 계층형 트리 구성 규칙을 정의합니다.

---

## 1. 개요
DDUK ERP는 실무형 ERP로서 제조업, 유통업, 일반 기업 회계를 모두 지원할 수 있도록 150개 이상의 표준 계정과목(CoA) Seed 데이터를 제공합니다. 
이는 재무상태표(BS) 및 손익계산서(PL) 구조를 완벽히 대응하며, 확장 가능한 계층형(Tree) 구조로 설계되었습니다.

### 1.1 요약 통계
- **총 계정 개수**: 약 170개
- **대분류별 계정 수**:
  - 자산(ASSET): 약 50개 (유동자산, 비유동자산)
  - 부채(LIABILITY): 약 30개 (유동부채, 비유동부채)
  - 자본(EQUITY): 약 15개 (자본금, 자본잉여금, 이익잉여금 등)
  - 수익(REVENUE): 약 15개 (매출액, 영업외수익)
  - 비용(EXPENSE): 약 60개 (매출원가, 판매비와관리비, 제조경비, 영업외비용 등)

---

## 2. 계정 코드 규칙 (Code Rules)
모든 계정과목은 **4자리 코드 체계**를 유지하며, 실무 스타일의 계층적 번호 부여 방식을 따릅니다.

| 번호 대역 | 구분 | 영문명 | 정상잔액(Normal Balance) | 비고 |
| :--- | :--- | :--- | :--- | :--- |
| `1000`번대 | 자산 | ASSET | 차변 (DEBIT) | 재고자산, 유형/무형자산 포함 |
| `2000`번대 | 부채 | LIABILITY | 대변 (CREDIT) | 매입채무, 차입금 등 |
| `3000`번대 | 자본 | EQUITY | 대변 (CREDIT) | 자본금, 잉여금 등 |
| `4000`번대 | 수익 | REVENUE | 대변 (CREDIT) | 상품/제품/용역 매출 등 |
| `5000`번대 | 비용(판관/매출원가) | EXPENSE | 차변 (DEBIT) | 판매비와관리비, 매출원가 |
| `6000`번대 | 비용(제조원가) | EXPENSE | 차변 (DEBIT) | 공장 제조경비 (원가계산용) |
| `7000`번대 | 비용(영업외) | EXPENSE | 차변 (DEBIT) | 금융비용, 재해손실 등 |
| `8000`번대 | 법인세 | EXPENSE | 차변 (DEBIT) | 법인세비용 |

---

## 3. 계층형 트리 구조 (Tree Hierarchy)
계정과목은 상위 그룹핑을 위한 부모 계정과, 실제 전표 입력이 가능한 말단 자식 계정으로 분리됩니다.

### 3.1 트리 속성 정의
- `parentCode`: 상위 계정의 코드를 참조하여 트리를 형성합니다 (null일 경우 Root 계정).
- `allowPosting`:
  - **상위 계정 (Parent Node)**: `false` (단순 그룹핑 용도이므로 전표 입력 불가)
  - **말단 계정 (Leaf Node)**: `true` (실제 전표 기표 시 선택 가능)
- `systemAccount`: `true`인 경우 사용자가 코드를 삭제하거나 주요 속성을 임의로 변경할 수 없습니다 (ERP 핵심 보호 계정).

### 3.2 계층 구조 예시
```text
1000 자산 (allowPosting: false)
 ├─ 1100 유동자산 (allowPosting: false)
 │   ├─ 1110 당좌자산 (allowPosting: false)
 │   │   ├─ 1111 현금 (allowPosting: true)
 │   │   └─ 1112 보통예금 (allowPosting: true)
 │   └─ 1120 매출채권 (allowPosting: false)
 │       └─ 1121 외상매출금 (allowPosting: true)
 └─ 1200 비유동자산 (allowPosting: false)
```

---

## 4. 데이터 무결성 규칙 (Data Quality Rules)
모든 계정은 DB 입력 시 및 API를 통한 갱신 시 다음의 룰을 검증받습니다.

1. **차대변(Normal Balance) 강제 매핑**
   - 유형이 `ASSET` 또는 `EXPENSE` 인 경우 `normalBalance`는 무조건 `DEBIT` 이어야 합니다.
   - 유형이 `LIABILITY`, `EQUITY`, `REVENUE` 인 경우 `normalBalance`는 무조건 `CREDIT` 이어야 합니다.
2. **순환 참조(Cycle) 방지**
   - 특정 계정의 `parentCode`를 자기 자신의 코드 혹은 자신의 하위 계정 코드로 지정할 수 없습니다.
3. **코드 중복 금지**
   - 계정 코드는 Unique Key로 관리되며 중복 배정할 수 없습니다.

---

## 5. 대표 계정 예시

| 코드 | 계정명 | 영문명 | 속성 | 정상잔액 |
| :--- | :--- | :--- | :--- | :--- |
| `1111` | 현금 | Cash | 당좌자산 (Leaf) | DEBIT |
| `1152` | 제품 | Finished Goods | 재고자산 (Leaf) | DEBIT |
| `2111` | 외상매입금 | Accounts Payable | 매입채무 (Leaf) | CREDIT |
| `4110` | 상품매출 | Sales of Merchandise | 수익 (Leaf) | CREDIT |
| `5210` | 급여 | Salaries & Wages | 판관비 (Leaf) | DEBIT |
| `6010` | 생산직임금 | Wages of Production Workers | 제조원가 (Leaf) | DEBIT |

---

## 6. Verification / Notes / Migration

### Verification
- `ChartOfAccountsInitializer`에 의해 앱 기동 시 `backend/src/main/resources/seeds/chart-of-accounts/default_coa.json`가 자동 파싱되어 DB에 적재됩니다.
- 초기 적재 후 `GET /api/v1/accounts/tree` API를 통해 O(N) 시간복잡도로 완성된 계층 트리 데이터를 조회할 수 있습니다.

### Notes
- 레거시 하드코딩 코드(1001, 1002 등)는 삭제하거나 신규 1000번대 계정과 통합할 수 있도록 Seed 구조를 유연하게 적용했습니다.
- 멀티테넌트(Multi-tenant)가 향후 도입될 경우, Tenant별 독립적인 CoA를 가질 수 있도록 현재 `systemAccount` 플래그로 전사 공통 필수 계정을 분리해 두었습니다.

### Migration
- 향후 추가되는 계정과목은 `default_coa.json`에 추가하면, 시스템 재시작 시 멱등성(Idempotency)을 바탕으로 기존 계정은 Update, 신규 계정은 Insert 처리됩니다.

---
마지막 업데이트: 2026-05-21
작성자: AI 보조 반영
