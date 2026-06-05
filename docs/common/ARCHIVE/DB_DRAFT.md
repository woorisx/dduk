# DDUK ERP DB 테이블 초안

이 문서는 현재 프로젝트 코드와 MVP 범위를 기준으로 정리한 DB 초안이다.  
목표는 "지금 팀이 구현 가능한 최소 계약"을 먼저 고정하는 것이고, 대형 ERP식 과설계는 뒤로 미룬다.

---

## 1. 현재 추천 방향

- 인증은 당분간 `members` 단일 테이블 기준으로 간다.
- 역할은 `ADMIN`, `HR`, `INVENTORY` 3개 값으로 단순화한다.
- `hr`, `inventory` 핵심 업무 테이블을 먼저 닫고, 메뉴 권한/복잡한 승인/AI 전용 구조는 2차로 미룬다.
- 공통 시간 컬럼은 `created_at`, `updated_at`을 사용한다.
- 아직 멀티테넌트는 도입하지 않으므로 `tenant_id`는 넣지 않는다.

왜 이렇게 가는가:

- 현재 백엔드 인증 코드가 `members` 구조를 이미 사용 중이다.
- `users + roles + user_roles` 구조로 지금 갈아타면 MVP보다 구조 정리에 시간이 더 많이 든다.
- 팀 프로젝트 단계에서는 "확장성 있는 단순 구조"가 "이론적으로 더 정교한 구조"보다 낫다.

---

## 2. 팀 설계 가이드

DB 초안을 수정하거나 컬럼을 추가하기 전에 아래 기준을 먼저 본다.

### 2.1 먼저 팀에서 합의할 것

- 인증 기준 테이블을 무엇으로 둘지
  - 현재 추천 방향은 `members` 기준이다.
- 역할 값을 무엇으로 고정할지
  - 현재 추천 값은 `ADMIN`, `HR`, `INVENTORY`다.
- 직원과 로그인 계정을 분리할지 연결할지
  - 현재 추천 방향은 `employees.member_id` nullable 연결이다.
- 거래처, 품목, 재고, 발주의 핵심 관계를 어떻게 둘지
  - 예: 기본 공급처만 둘지, 다중 공급 구조까지 바로 갈지
- 상태값을 어디까지 세분화할지
  - 예: 발주 상태를 `REQUESTED -> APPROVED -> ORDERED -> RECEIVED` 수준으로 둘지 더 세분화할지

### 2.2 컬럼 추가할 때 기준

- 기존 계약을 깨지 않는 방향으로 추가한다.
- 가능하면 nullable 컬럼 또는 기본값이 있는 컬럼을 우선한다.
- `NOT NULL` 신규 컬럼은 기존 데이터, 시드 데이터, API, 프론트 영향까지 같이 확인한다.
- 계좌번호, 사업자번호, 전화번호처럼 형식이 고정되지 않은 값은 숫자형보다 문자열로 관리한다.
- 이미 코드에서 사용 중인 테이블명, 컬럼명, 상태값은 합의 없이 임의로 바꾸지 않는다.

### 2.3 민감정보와 운영정보 기준

- 비밀번호, 토큰, 시크릿은 DB 초안이나 시드에 평문으로 두지 않는다.
- 계좌정보, 연락처, 이메일은 저장 여부뿐 아니라 화면/API 노출 범위까지 같이 본다.
- 감사성 추적이 필요한 변경은 `audit_logs` 같은 이력 구조 필요성을 같이 검토한다.

### 2.4 과설계 방지 기준

- MVP에서 실제로 안 쓰는 권한 세분화 테이블은 미룬다.
- 아직 필요하지 않은 다단계 승인 구조는 미룬다.
- 멀티테넌트가 도입되지 않았다면 `tenant_id`는 넣지 않는다.
- AI/RPA 전용 작업 큐나 이력 테이블은 실제 기능이 들어갈 때 만든다.

### 2.5 DB 변경 제안할 때 같이 남길 것

- 왜 필요한지
- 어느 도메인인지
- 백엔드 / API / 프론트 영향 범위
- migration 또는 시드 수정 필요 여부

---

## 3. 도메인 구분

| 도메인 | 설명 |
| :--- | :--- |
| `admin` | 로그인, 권한, 계정 상태, 감사 로그 |
| `hr` | 직원, 근태, 급여, 비용 |
| `inventory` | 거래처, 품목, 재고, 발주, 입출고 |

---

## 4. 1차 MVP에서 먼저 확정할 테이블

1. `members`
2. `employees`
3. `attendances`
4. `vendors`
5. `items`
6. `inventories`
7. `purchase_orders`
8. `purchase_order_items`

이 8개면 현재 프로젝트의 로그인, 직원관리, 근태, 거래처, 품목, 재고, 발주 흐름을 대부분 커버할 수 있다.

---

## 5. `admin` 추천 구조

### 5.1 `members`
시스템 로그인 계정

| 컬럼 | 타입 예시 | 설명 |
| :--- | :--- | :--- |
| `id` | bigint | PK |
| `login_id` | varchar(50) | 로그인 ID, unique |
| `password` | varchar(255) | BCrypt 해시 비밀번호 |
| `name` | varchar(100) | 사용자명 |
| `role` | varchar(30) | `ADMIN`, `HR`, `INVENTORY` |
| `active` | tinyint | 활성 여부 |
| `last_login_at` | datetime | 마지막 로그인 시각, nullable |
| `created_at` | datetime | 생성 시각 |
| `updated_at` | datetime | 수정 시각 |

권장 제약:

- `login_id` unique
- `role` 인덱스
- `active` 인덱스

비고:

- 현재 코드와 가장 잘 맞는 인증 구조다.
- 초기에는 역할을 별도 테이블로 빼지 않고 단일 컬럼으로 관리해도 충분하다.

### 5.2 `audit_logs`
중요 행위 감사 로그

| 컬럼 | 타입 예시 | 설명 |
| :--- | :--- | :--- |
| `id` | bigint | PK |
| `member_id` | bigint | FK -> `members.id`, nullable |
| `action` | varchar(100) | 예: `LOGIN`, `UPDATE_MEMBER_ROLE` |
| `target_type` | varchar(50) | 대상 리소스 종류 |
| `target_id` | bigint | 대상 리소스 ID, nullable |
| `details` | text | 변경 요약 JSON 또는 텍스트 |
| `ip_address` | varchar(64) | 요청 IP |
| `user_agent` | varchar(255) | 클라이언트 정보 |
| `created_at` | datetime | 발생 시각 |

비고:

- 이 테이블은 유용하지만 MVP 2차로 미뤄도 된다.

---

## 6. `hr` 추천 구조

### 6.1 `employees`
직원 기본 정보

| 컬럼 | 타입 예시 | 설명 |
| :--- | :--- | :--- |
| `id` | bigint | PK |
| `member_id` | bigint | FK -> `members.id`, nullable |
| `employee_no` | varchar(50) | 사번, unique |
| `name` | varchar(100) | 직원명 |
| `department` | varchar(100) | 부서명 |
| `position` | varchar(100) | 직책 |
| `employment_status` | varchar(30) | `ACTIVE`, `LEAVE`, `RESIGNED` |
| `hire_date` | date | 입사일 |
| `email` | varchar(100) | 이메일 |
| `phone` | varchar(30) | 연락처 |
| `created_at` | datetime | 생성 시각 |
| `updated_at` | datetime | 수정 시각 |

비고:

- 계정이 없는 직원도 등록할 수 있게 `member_id`는 nullable로 둔다.
- 부서는 초기에 문자열로 두고, 별도 조직 테이블은 나중에 검토한다.

### 6.2 `attendances`
근태 기록

| 컬럼 | 타입 예시 | 설명 |
| :--- | :--- | :--- |
| `id` | bigint | PK |
| `employee_id` | bigint | FK -> `employees.id` |
| `work_date` | date | 근무일 |
| `check_in_at` | datetime | 출근 시각 |
| `check_out_at` | datetime | 퇴근 시각 |
| `status` | varchar(30) | `PRESENT`, `LATE`, `ABSENT`, `LEAVE` |
| `note` | varchar(255) | 비고 |
| `created_at` | datetime | 생성 시각 |
| `updated_at` | datetime | 수정 시각 |

권장 제약:

- `employee_id`, `work_date` unique 조합

### 6.3 `payrolls`
월별 급여 결과

| 컬럼 | 타입 예시 | 설명 |
| :--- | :--- | :--- |
| `id` | bigint | PK |
| `employee_id` | bigint | FK -> `employees.id` |
| `pay_month` | char(7) | 예: `2026-05` |
| `base_salary` | decimal(15,2) | 기본급 |
| `allowance_amount` | decimal(15,2) | 수당 |
| `deduction_amount` | decimal(15,2) | 공제 |
| `net_salary` | decimal(15,2) | 실지급액 |
| `status` | varchar(30) | `DRAFT`, `CONFIRMED`, `PAID` |
| `paid_at` | datetime | 지급 시각, nullable |
| `created_at` | datetime | 생성 시각 |
| `updated_at` | datetime | 수정 시각 |

비고:

- 급여까지 바로 갈 계획이 아니면 2차로 미뤄도 된다.

### 6.4 `expenses`
회사 비용 지출 기록

| 컬럼 | 타입 예시 | 설명 |
| :--- | :--- | :--- |
| `id` | bigint | PK |
| `employee_id` | bigint | FK -> `employees.id`, nullable |
| `expense_date` | date | 지출일 |
| `category` | varchar(50) | 비용 분류 |
| `amount` | decimal(15,2) | 금액 |
| `description` | varchar(255) | 내용 |
| `receipt_file_path` | varchar(255) | 영수증 파일 경로, nullable |
| `status` | varchar(30) | `SUBMITTED`, `APPROVED`, `REJECTED` |
| `created_at` | datetime | 생성 시각 |
| `updated_at` | datetime | 수정 시각 |

비고:

- 비용 관리가 일정에 없다면 이 테이블도 2차로 미루는 편이 낫다.

---

## 7. `inventory` 추천 구조

### 7.1 `vendors`
거래처 정보

| 컬럼 | 타입 예시 | 설명 |
| :--- | :--- | :--- |
| `id` | bigint | PK |
| `vendor_code` | varchar(50) | 거래처 코드, unique |
| `business_registration_no` | varchar(20) | 사업자등록번호, unique |
| `name` | varchar(100) | 거래처명 |
| `representative_name` | varchar(100) | 대표자명 |
| `business_type` | varchar(100) | 업태, nullable |
| `business_item` | varchar(100) | 종목, nullable |
| `contact_name` | varchar(100) | 담당자명 |
| `contact_phone` | varchar(30) | 연락처 |
| `email` | varchar(100) | 이메일 |
| `address` | varchar(255) | 주소 |
| `status` | varchar(30) | `ACTIVE`, `INACTIVE` |
| `bank_name` | varchar(50) | 은행명, nullable |
| `bank_account_no` | varchar(50) | 계좌번호, nullable |
| `bank_account_holder` | varchar(100) | 예금주명, nullable |
| `bankbook_copy_file_path` | varchar(255) | 통장사본 경로, nullable |
| `memo` | varchar(255) | 메모, nullable |
| `created_at` | datetime | 생성 시각 |
| `updated_at` | datetime | 수정 시각 |

### 7.2 `items`
관리 품목

| 컬럼 | 타입 예시 | 설명 |
| :--- | :--- | :--- |
| `id` | bigint | PK |
| `item_code` | varchar(50) | 품목 코드, unique |
| `name` | varchar(100) | 품목명 |
| `category` | varchar(100) | 카테고리 |
| `spec` | varchar(100) | 규격, nullable |
| `barcode` | varchar(100) | 바코드, nullable |
| `unit` | varchar(30) | 단위 예: `EA`, `BOX` |
| `default_vendor_id` | bigint | FK -> `vendors.id`, nullable |
| `unit_price` | decimal(15,2) | 기본 단가 |
| `safety_stock` | int | 안전 재고 |
| `is_active` | tinyint | 사용 여부 |
| `created_at` | datetime | 생성 시각 |
| `updated_at` | datetime | 수정 시각 |

### 7.3 `inventories`
현재 재고 수량

| 컬럼 | 타입 예시 | 설명 |
| :--- | :--- | :--- |
| `id` | bigint | PK |
| `item_id` | bigint | FK -> `items.id` |
| `location` | varchar(100) | 창고/보관 위치 |
| `quantity` | int | 현재 수량 |
| `allocated_quantity` | int | 예약/할당 수량 |
| `lot_no` | varchar(100) | LOT 번호, 기본값 빈 문자열 |
| `expiration_date` | date | 유통기한, 기본값 `9999-12-31` |
| `status` | varchar(30) | `AVAILABLE`, `HOLD`, `DAMAGED`, `EXPIRED` |
| `last_adjusted_at` | datetime | 마지막 조정 시각 |
| `created_at` | datetime | 생성 시각 |
| `updated_at` | datetime | 수정 시각 |

권장 제약:

- `item_id`, `location` unique 조합
- `quantity >= 0`
- `allocated_quantity >= 0`
- `allocated_quantity <= quantity`

비고:

- 단일 창고여도 `location` 컬럼은 남겨 두는 편이 이후 확장에 유리하다.
- LOT/유통기한을 unique 키에 같이 묶을 계획이면 `NULL` 대신 기본값을 두는 편이 중복 제어에 안전하다.

### 7.4 `purchase_orders`
발주 헤더

| 컬럼 | 타입 예시 | 설명 |
| :--- | :--- | :--- |
| `id` | bigint | PK |
| `purchase_order_no` | varchar(50) | 발주번호, unique |
| `vendor_id` | bigint | FK -> `vendors.id` |
| `requested_by_member_id` | bigint | FK -> `members.id` |
| `approved_by_member_id` | bigint | FK -> `members.id`, nullable |
| `order_date` | date | 발주일 |
| `expected_date` | date | 입고 예정일, nullable |
| `status` | varchar(30) | `REQUESTED`, `APPROVED`, `ORDERED`, `RECEIVED`, `CANCELED` |
| `total_amount` | decimal(15,2) | 총액 |
| `note` | varchar(255) | 비고 |
| `created_at` | datetime | 생성 시각 |
| `updated_at` | datetime | 수정 시각 |

### 7.5 `purchase_order_items`
발주 상세 품목

| 컬럼 | 타입 예시 | 설명 |
| :--- | :--- | :--- |
| `id` | bigint | PK |
| `purchase_order_id` | bigint | FK -> `purchase_orders.id` |
| `item_id` | bigint | FK -> `items.id` |
| `quantity` | int | 발주 수량 |
| `unit` | varchar(30) | 발주 단위 |
| `unit_price` | decimal(15,2) | 발주 단가 |
| `supply_amount` | decimal(15,2) | 공급가액 |
| `tax_amount` | decimal(15,2) | 부가세 |
| `line_amount` | decimal(15,2) | 행 금액 |
| `expected_date` | date | 품목별 입고 예정일, nullable |
| `note` | varchar(255) | 비고, nullable |
| `created_at` | datetime | 생성 시각 |
| `updated_at` | datetime | 수정 시각 |

### 7.6 `stock_movements`
입출고 이력

| 컬럼 | 타입 예시 | 설명 |
| :--- | :--- | :--- |
| `id` | bigint | PK |
| `item_id` | bigint | FK -> `items.id` |
| `inventory_id` | bigint | FK -> `inventories.id` |
| `movement_type` | varchar(30) | `IN`, `OUT`, `ADJUST` |
| `quantity` | int | 변동 수량 |
| `reference_type` | varchar(50) | `PURCHASE_ORDER`, `MANUAL` |
| `reference_id` | bigint | 참조 대상 ID, nullable |
| `moved_at` | datetime | 변동 시각 |
| `note` | varchar(255) | 비고 |
| `created_at` | datetime | 생성 시각 |

비고:

- 재고 조정/입출고 이력 추적이 필요해질 때 붙이면 된다.

---

## 8. 핵심 관계 요약

```text
members 1---0..1 employees
members 1---N purchase_orders (requested_by_member_id)
members 1---N purchase_orders (approved_by_member_id)
members 1---N audit_logs

employees 1---N attendances
employees 1---N payrolls
employees 1---N expenses

vendors 1---N items
vendors 1---N purchase_orders
items 1---N inventories
purchase_orders 1---N purchase_order_items
items 1---N purchase_order_items
items 1---N stock_movements
inventories 1---N stock_movements
```

---

## 9. 2차 확장으로 미루는 것

- 복잡한 메뉴 권한 테이블
- `users + roles + user_roles` 재구성
- 다단계 승인 구조
- 급여/비용 세부 확장
- AI/RPA 전용 작업 큐/이력 테이블

이 영역은 기능이 실제로 필요해진 뒤 추가하는 편이 안전하다.

---

## 10. 팀 체크 포인트

- `members`를 MVP 인증 기준으로 팀이 합의했는지
- `employees.member_id`를 nullable로 유지할지
- `inventory.location`을 단일 문자열로 시작할지
- 발주 승인 흐름을 단일 승인으로 둘지
- 급여/비용을 1차에 포함할지 2차로 미룰지

---

## 11. 다음 단계

1. 이 문서를 기준으로 SQL 초안과 맞춘다.
2. API 초안의 필드명과 엔드포인트를 다시 맞춘다.
3. 실제 JPA Entity 또는 마이그레이션 SQL로 옮긴다.
4. `admin -> hr -> inventory` 순서로 구현을 닫는다.
