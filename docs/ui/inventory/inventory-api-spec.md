# 재고관리 모듈 API 명세서 (Inventory Module API Specification)

이 문서는 DDUK ERP 재고관리 모듈의 공식 API 명세서이다. 모든 API는 기본 prefix `/api/v1`을 사용하며, HTTP 상태코드와 하네스 표준 응답 포맷을 준수한다.
본 명세서는 **Zero DB Modification(DB 스키마 무변경)** 정책에 의해 설계 및 구현되었다.

* **상태**: 구현 완료 (Approved / Implemented)
* **최종 수정일**: 2026-05-28
* **데이터베이스 수정 여부**: **무변경 (No schema change, No columns added, No migration)**

---

## 1. 공통 응답 규격 (AI Harness 표준)

### 성공 응답 (HTTP 200 OK)
```json
{
  "status": "success",
  "data": {},
  "message": "요청이 완료되었습니다."
}
```

### 실패 응답 (HTTP 400 Bad Request, 500 Internal Error 등)
```json
{
  "status": "error",
  "message": "에러 상세 메시지",
  "code": "ERROR_CODE"
}
```

---

## 2. 재고 현황 및 조회 API

### 2.1. 재고 목록 조회
* **Endpoint**: `GET /api/v1/inventories`
* **Query Parameters**:
  * `warehouseId` (Long, Optional): 특정 창고 필터
  * `itemId` (Long, Optional): 특정 품목 필터
  * `lowStockOnly` (Boolean, Optional): 안전재고 이하 부족 품목만 조회 여부
* **Response Data (`data`)**:
```json
[
  {
    "id": 1,
    "item": {
      "id": 1,
      "itemCode": "ITM-0001",
      "name": "원자재 찹쌀가루",
      "category": "원자재",
      "unit": "KG",
      "safetyStock": 100
    },
    "warehouse": {
      "id": 1,
      "warehouseCode": "WH-MAIN",
      "warehouseName": "본사 메인 창고"
    },
    "currentStock": 150,
    "allocatedStock": 20,
    "safetyStock": 100,
    "averageCost": 1200.0000,
    "inventoryValue": 180000.0000,
    "availableStock": 130
  }
]
```

### 2.2. 창고 목록 조회
* **Endpoint**: `GET /api/v1/warehouses`
* **Response Data (`data`)**:
```json
[
  {
    "id": 1,
    "warehouseCode": "WH-MAIN",
    "warehouseName": "본사 메인 창고",
    "location": "서울시 마포구",
    "status": "ACTIVE"
  }
]
```

---

## 3. 입출고 이력 및 거래 원장 API

### 3.1. 재고 거래 이력 조회
* **Endpoint**: `GET /api/v1/inventories/stock-movements`
* **Query Parameters**:
  * `warehouseId` (Long, Optional): 창고 필터
  * `itemId` (Long, Optional): 품목 필터
  * `movementType` (String, Optional): 이력 유형 (`INBOUND`, `OUTBOUND`, `TRANSFER_IN`, `TRANSFER_OUT`, `ADJUSTMENT_IN`, `ADJUSTMENT_OUT`, `RETURN_IN`, `RETURN_OUT`)
* **Response Data (`data`)**:
```json
[
  {
    "id": 10,
    "item": {
      "id": 1,
      "itemCode": "ITM-0001",
      "name": "원자재 찹쌀가루"
    },
    "warehouse": {
      "id": 1,
      "warehouseName": "본사 메인 창고"
    },
    "movementType": "TRANSFER_IN",
    "movementReason": "TRANSFER",
    "referenceNo": "TRF-IN-20260527-0001",
    "quantity": 100,
    "unitCost": 1200.0000,
    "totalAmount": 120000.0000,
    "beforeQuantity": 50,
    "afterQuantity": 150,
    "referenceType": "WAREHOUSE_TRANSFER",
    "referenceId": "1",
    "createdAt": "2026-05-27T10:00:00"
  }
]
```

---

## 4. 창고 이동 승인 관리 API (Zero DB 고도화 사양)

### 4.1. 창고 이동 요청 등록 (다중 품목 일괄 지원)
* **Endpoint**: `POST /api/v1/warehouse-transfers`
* **Request Body**:
```json
{
  "sourceWarehouseId": 1,
  "targetWarehouseId": 2,
  "remarks": "인천 지사 창고 재고 부족분 긴급 이동 요청",
  "items": [
    {
      "itemId": 1,
      "quantity": 50
    },
    {
      "itemId": 2,
      "quantity": 100
    }
  ]
}
```
* **Response Data (`data`)**:
```json
{
  "id": 1,
  "transferNo": "TRF-20260528-0001",
  "sourceWarehouseName": "본사 메인 창고",
  "targetWarehouseName": "인천 지사 창고",
  "status": "PENDING",
  "remarks": "인천 지사 창고 재고 부족분 긴급 이동 요청",
  "createdAt": "2026-05-28T11:00:00",
  "items": [
    { "itemId": 1, "itemCode": "ITM-0001", "itemName": "원자재 찹쌀가루", "unit": "KG", "quantity": 50 },
    { "itemId": 2, "itemCode": "ITM-0002", "itemName": "얼그레이 티백", "unit": "BOX", "quantity": 100 }
  ]
}
```

### 4.2. 창고 이동 목록 조회
* **Endpoint**: `GET /api/v1/warehouse-transfers`
* **Query Parameters**:
  * `status` (String, Optional): 상태 필터 (`PENDING`, `APPROVED`, `COMPLETED`, `CANCELLED`)
* **Response Data (`data`)**:
```json
[
  {
    "id": 1,
    "transferNo": "TRF-20260528-0001",
    "sourceWarehouseId": 1,
    "sourceWarehouseName": "본사 메인 창고",
    "targetWarehouseId": 2,
    "targetWarehouseName": "인천 지사 창고",
    "status": "PENDING",
    "remarks": "인천 지사 창고 재고 부족분 긴급 이동 요청",
    "requestedByName": "김재고 대리",
    "createdAt": "2026-05-28T11:00:00"
  }
]
```

### 4.3. 창고 이동 상세 조회 (사유 및 동적 감사 타임라인 제공)
* **Endpoint**: `GET /api/v1/warehouse-transfers/{id}`
* **Response Data (`data`)**:
```json
{
  "id": 1,
  "transferNo": "TRF-20260528-0001",
  "sourceWarehouseId": 1,
  "sourceWarehouseName": "본사 메인 창고",
  "targetWarehouseId": 2,
  "targetWarehouseName": "인천 지사 창고",
  "status": "CANCELLED",
  "remarks": "[반려] 사유: 가용 재고 한도 초과로 반려 처리함.",
  "requestedByName": "김재고 대리",
  "approvedByName": "박팀장 과장",
  "createdAt": "2026-05-28T11:00:00",
  "approvedAt": "2026-05-28T11:30:00",
  "completedAt": null,
  "items": [
    {
      "itemId": 1,
      "itemCode": "ITM-0001",
      "itemName": "원자재 찹쌀가루",
      "unit": "KG",
      "quantity": 50
    }
  ]
}
```

### 4.4. 창고 이동 승인
* **Endpoint**: `POST /api/v1/warehouse-transfers/{id}/approve`
* **Response Data (`data`)**:
```json
{
  "status": "success",
  "data": {
    "id": 1,
    "transferNo": "TRF-20260528-0001",
    "status": "APPROVED",
    "approvedByName": "박팀장 과장",
    "approvedAt": "2026-05-28T11:30:00"
  }
}
```

### 4.5. 창고 이동 완료 처리 (멱등성 및 정합성 보장)
* **Endpoint**: `POST /api/v1/warehouse-transfers/{id}/complete`
* **Response Data (`data`)**:
```json
{
  "status": "success",
  "data": {
    "id": 1,
    "transferNo": "TRF-20260528-0001",
    "status": "COMPLETED",
    "completedAt": "2026-05-28T12:00:00"
  }
}
```

### 4.6. 창고 이동 취소 및 반려 (Zero DB remarks 사유 결합)
* **Endpoint**: `POST /api/v1/warehouse-transfers/{id}/cancel`
* **Request Body (Optional)**:
```json
{
  "reason": "중복 신청으로 인한 요청 반려",
  "type": "반려"
}
```
* **Response Data (`data`)**:
```json
{
  "id": 1,
  "transferNo": "TRF-20260528-0001",
  "status": "CANCELLED",
  "remarks": "[반려] 사유: 중복 신청으로 인한 요청 반려"
}
```
* **비고**: 별도 컬럼을 증설하지 않고 `remarks` 필드 내에 구조화 문자열 `[반려] 사유: ...` 형태로 이력을 바인딩하여 무결성을 유지합니다.

---

## 5. 재고 대시보드 API (Read Model 기반)

### 5.1. 대시보드 종합 통계 조회
* **Endpoint**: `GET /api/v1/inventory/dashboard/stats`
* **Response Data (`data`)**:
```json
{
  "totalQuantity": 4350,
  "totalValue": 8940000.0000,
  "lowStockCount": 3,
  "outboundVolume30Days": 1250,
  "pendingTransferCount": 2,
  "warehouseDistribution": [
    {
      "warehouseName": "본사 메인 창고",
      "totalStock": 3000,
      "totalValue": 6200000.0000
    }
  ],
  "recentMovements": [
    {
      "id": 10,
      "createdAt": "2026-05-28T10:00:00",
      "referenceNo": "TRF-IN-20260528-0001",
      "movementType": "TRANSFER_IN",
      "itemName": "원자재 찹쌀가루",
      "quantity": 100,
      "warehouseName": "본사 메인 창고"
    }
  ]
}
```
