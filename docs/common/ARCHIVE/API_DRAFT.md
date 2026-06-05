# DDUK ERP API 초안

이 문서는 프론트엔드, 백엔드, AI 서버가 동시에 작업할 수 있게 핵심 엔드포인트 초안을 정리한 문서다.  
현재 DB 초안과 백엔드 인증 DTO 기준으로 맞춘 MVP 착수용 계약이며, 최종 명세는 아니다.

---

## 1. 공통 규칙

- Base path: `/api/v1`
- Content-Type: `application/json`
- 인증 필요 API는 `Authorization: Bearer <token>` 사용
- 응답 포맷은 [docs/CONVENTION.md](/C:/kmh/dduk/docs/CONVENTION.md:102) 규칙을 따른다.
- 현재 인증 기준은 `members` 테이블이며 역할 값은 `ADMIN`, `HR`, `INVENTORY`를 사용한다.

---

## 2. 인증 / 관리자 (`admin`)

### 로그인

- `POST /api/v1/auth/login`

현재 백엔드 DTO 기준 요청 예시:

```json
{
  "loginId": "admin",
  "password": "admin123"
}
```

현재 백엔드 DTO 기준 응답 예시:

```json
{
  "token": "jwt-access-token",
  "loginId": "admin",
  "name": "System Admin",
  "role": "ADMIN"
}
```

비고:

- 현재 구현은 공통 래퍼 없이 `LoginResponseDto` 본문을 바로 반환한다.
- 추후 공통 응답 래퍼를 도입하면 문서도 함께 맞춘다.

### 내 정보 조회

- `GET /api/v1/auth/me`

예상 응답 예시:

```json
{
  "loginId": "admin",
  "name": "System Admin",
  "role": "ADMIN",
  "active": true
}
```

### 관리자 계정 목록 조회

- `GET /api/v1/admin/members`

예상 응답 필드:

- `id`
- `loginId`
- `name`
- `role`
- `active`
- `createdAt`

### 관리자 계정 생성

- `POST /api/v1/admin/members`

요청 예시:

```json
{
  "loginId": "inventory",
  "password": "inv123",
  "name": "Inventory Manager",
  "role": "INVENTORY"
}
```

### 관리자 계정 권한 변경

- `PATCH /api/v1/admin/members/{memberId}/role`

요청 예시:

```json
{
  "role": "HR"
}
```

### 관리자 계정 활성 상태 변경

- `PATCH /api/v1/admin/members/{memberId}/status`

요청 예시:

```json
{
  "active": false
}
```

### 관리자 대시보드 요약

- `GET /api/v1/admin/dashboard`

예상 항목:

- 총 직원 수
- 오늘 출근 수
- 재고 부족 품목 수
- 대기 중 발주 수

---

## 3. 인사 / 회계 (`hr`)

### 직원 목록 조회

- `GET /api/v1/employees?page=0&size=20&sort=createdAt,desc`

예상 필드:

- `id`
- `employeeNo`
- `name`
- `department`
- `position`
- `employmentStatus`
- `email`
- `phone`
- `memberId`

### 직원 등록

- `POST /api/v1/employees`

요청 예시:

```json
{
  "employeeNo": "EMP-0001",
  "name": "홍길동",
  "department": "HR",
  "position": "Manager",
  "employmentStatus": "ACTIVE",
  "hireDate": "2026-05-13",
  "email": "hong@example.com",
  "phone": "010-1234-5678",
  "memberId": null
}
```

### 직원 상세 조회

- `GET /api/v1/employees/{employeeId}`

### 근태 목록 조회

- `GET /api/v1/attendances?employeeId=1&month=2026-05`

예상 필드:

- `id`
- `employeeId`
- `workDate`
- `checkInAt`
- `checkOutAt`
- `status`
- `note`

### 근태 등록

- `POST /api/v1/attendances`

요청 예시:

```json
{
  "employeeId": 1,
  "workDate": "2026-05-13",
  "checkInAt": "2026-05-13T09:00:00",
  "checkOutAt": "2026-05-13T18:00:00",
  "status": "PRESENT",
  "note": ""
}
```

### 2차 확장 API

- `GET /api/v1/payrolls/{employeeId}?month=2026-05`
- 비용/급여 관련 생성·승인 API

비고:

- `payrolls`, `expenses`는 DB 초안에서도 2차 확장 범위다.

---

## 4. 구매 / 재고 (`inventory`)

### 거래처 목록 조회

- `GET /api/v1/vendors`

예상 필드:

- `id`
- `vendorCode`
- `businessRegistrationNo`
- `name`
- `representativeName`
- `businessType`
- `businessItem`
- `contactName`
- `contactPhone`
- `email`
- `address`
- `status`
- `bankName`
- `bankAccountNo`
- `bankAccountHolder`
- `bankbookCopyFilePath`
- `memo`

### 거래처 등록

- `POST /api/v1/vendors`

요청 예시:

```json
{
  "vendorCode": "VENDOR-001",
  "businessRegistrationNo": "123-45-67890",
  "name": "뚝상사",
  "representativeName": "김대표",
  "businessType": "도소매",
  "businessItem": "사무용품",
  "contactName": "김담당",
  "contactPhone": "02-1234-5678",
  "email": "vendor@example.com",
  "address": "서울시 중구 ...",
  "status": "ACTIVE",
  "bankName": "국민은행",
  "bankAccountNo": "123456-01-123456",
  "bankAccountHolder": "뚝상사",
  "bankbookCopyFilePath": null,
  "memo": ""
}
```

### 품목 목록 조회

- `GET /api/v1/items`

### 품목 등록

- `POST /api/v1/items`

요청 예시:

```json
{
  "itemCode": "ITEM-001",
  "name": "복사용지 A4",
  "category": "OFFICE",
  "spec": "A4 / 80g",
  "barcode": "880000000001",
  "unit": "BOX",
  "defaultVendorId": 1,
  "unitPrice": 23000,
  "safetyStock": 10,
  "isActive": true
}
```

### 재고 수량 조회

- `GET /api/v1/inventories`

예상 필드:

- `id`
- `itemId`
- `location`
- `quantity`
- `allocatedQuantity`
- `lotNo`
- `expirationDate`
- `status`
- `lastAdjustedAt`

### 재고 수량 조정

- `PATCH /api/v1/inventories/{inventoryId}`

요청 예시:

```json
{
  "quantity": 120,
  "allocatedQuantity": 20,
  "location": "MAIN",
  "lotNo": "LOT-20260513",
  "expirationDate": "2027-05-12",
  "status": "AVAILABLE"
}
```

### 발주 목록 조회

- `GET /api/v1/purchase-orders`

### 발주 등록

- `POST /api/v1/purchase-orders`

요청 예시:

```json
{
  "purchaseOrderNo": "PO-2026-0001",
  "vendorId": 1,
  "requestedByMemberId": 1,
  "expectedDate": "2026-05-20",
  "note": "월간 사무용품 발주",
  "items": [
    {
      "itemId": 3,
      "quantity": 5,
      "unit": "BOX",
      "unitPrice": 23000,
      "supplyAmount": 115000,
      "taxAmount": 11500,
      "expectedDate": "2026-05-20",
      "note": ""
    }
  ]
}
```

### 발주 상태 변경

- `PATCH /api/v1/purchase-orders/{purchaseOrderId}/status`

요청 예시:

```json
{
  "status": "APPROVED",
  "approvedByMemberId": 1
}
```

상태 예시:

- `REQUESTED`
- `APPROVED`
- `ORDERED`
- `RECEIVED`
- `CANCELED`

### 2차 확장 API

- 재고 이동 이력 조회
- 자동 발주 추천 반영 API
- 다단계 승인 API

---

## 5. AI / 보조 기능

AI 서버와 백엔드를 분리할 경우 두 가지 방식 중 하나를 쓴다.

### 방식 A. 백엔드가 AI 서버를 호출

- 프론트 -> 백엔드 -> AI 서버
- 장점: 인증, 감사 로그, 권한 관리를 백엔드에서 통제하기 쉬움

### 방식 B. 프론트가 AI 서버를 직접 호출

- 프론트 -> AI 서버
- 장점: 단순하지만 권한/보안 정책이 흔들리기 쉬움

권장:

- 초기에는 방식 A를 기본으로 잡는다.

예시 엔드포인트:

- `POST /api/v1/ai/chat`
- `POST /api/v1/ai/inventory-summary`
- `POST /api/v1/ai/ocr/receipt`

비고:

- AI 기능은 ERP 핵심 CRUD가 붙은 뒤 연결하는 것을 우선한다.

---

## 6. 에러 처리 초안

예시:

```json
{
  "status": "error",
  "message": "유효하지 않은 직원 ID입니다.",
  "code": "EMPLOYEE_NOT_FOUND"
}
```

권장 코드:

- `INVALID_REQUEST`
- `UNAUTHORIZED`
- `FORBIDDEN`
- `MEMBER_NOT_FOUND`
- `EMPLOYEE_NOT_FOUND`
- `VENDOR_NOT_FOUND`
- `ITEM_NOT_FOUND`
- `INVENTORY_NOT_FOUND`
- `PURCHASE_ORDER_NOT_FOUND`

---

## 7. 프론트 작업 시작점

프론트는 아래 화면부터 만들면 된다.

1. 로그인 페이지
2. 관리자 대시보드
3. 관리자 계정 관리 페이지
4. 직원 목록 / 등록 페이지
5. 거래처 목록 / 등록 페이지
6. 품목 목록 / 등록 페이지
7. 재고 목록 페이지
8. 발주 목록 / 등록 페이지

각 화면은 먼저 mock 데이터로 만들고, API 연결은 백엔드 엔드포인트가 고정된 뒤 붙인다.

---

## 8. 다음에 확정할 것

- 정확한 페이지네이션 응답 구조
- 날짜/금액 포맷
- 검색 조건 표준
- 발주 등록 시 상세 품목 payload 최종 구조
- 파일 업로드 방식
- AI 서버 연동 인증 방식
