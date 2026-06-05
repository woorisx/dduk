# Warehouse Management

이 문서는 DDUK ERP 재고관리 도메인의 마스터 데이터인 창고(`warehouses`), 품목(`items`), 그리고 이들을 유기적으로 연결하는 실시간 창고별 재고 및 원가 계산 정책(`inventories`) 설명서다. DDUK ERP의 원가 정책과 이동평균단가 연산 규칙을 함께 정의한다.

---

## 1. 작업 개요

재고관리는 품목 마스터와 물리/논리 창고 마스터 데이터 구축에서 시작된다. 각 품목이 특정 창고에 입고/출고될 때 실시간 재고량과 함께 **이동평균단가(Moving Average Cost)** 방식을 기반으로 재고자산의 자산 평가액을 정밀하게 연산하고 추적 관리한다.

---

## 2. 변경 파일 목록

- `backend/src/main/java/com/dduk/entity/inventory/Warehouse.java`
- `backend/src/main/java/com/dduk/entity/inventory/Item.java`
- `backend/src/main/java/com/dduk/entity/inventory/Inventory.java`
- `backend/src/main/java/com/dduk/repository/inventory/WarehouseRepository.java`
- `backend/src/main/java/com/dduk/repository/inventory/ItemRepository.java`
- `backend/src/main/java/com/dduk/repository/inventory/InventoryRepository.java`
- `backend/src/main/resources/db/mysql/dduk_bootstrap_data.sql`
- `frontend/pages/inventory/list.html`

---

## 3. 원가 및 재고 관리 정책 (중요)

DDUK ERP는 안정적인 원가 계산 및 세무 검증을 위해 아래와 같은 엄격한 재고/원가 정책을 강제 적용한다.

### 3.1. 이동평균단가 재계산 정책
재고 유입(입고, 조정입고, 창고이동입고 등)이 발생할 때마다 아래 공식에 따라 해당 창고의 품목 평균단가를 실시간 재계산한다.

$$\text{신규이동평균단가} = \frac{\text{기존재고금액} + \text{신규입고금액}}{\text{기존수량} + \text{신규입고수량}}$$

- **소수점 처리 (Scale)**: 소수점 `4`자리 고정 (`DECIMAL(19,4)`)
- **반올림 정책 (Rounding Mode)**: `RoundingMode.HALF_UP` (사사오입 반올림)
- **음수 재고 허용 여부**: **절대 허용하지 않음 (음수 재고 전면 차단)**. 출고 또는 감산 중 현재고(`current_stock`)가 0보다 작아지는 연산 발생 시, 즉시 트랜잭션 예외를 일으키고 전면 롤백 처리한다.
- **0원 입고 처리**: 기증품, 샘플 수령 등 0원 입고 발생 시 분자는 증가하지 않고 분모만 증가하여 전체 평균단가가 하락하는 정상적인 이동평균단가 회계 연산을 준수한다.
- **0 division check (나눗셈 방어)**: 입고 수량이 없거나 분모가 0이 되는 연산 발생 시, 연산을 생략하고 기존 단가를 그대로 유지하여 시스템 크래시를 방지한다.
- **창고별 평균단가 정책**: 회사 전체 통합 단가가 아닌 **창고별(Location-based) 독립 이동평균단가** 정책을 채택하여 물류 거점별 자산 가치 왜곡을 원천 차단한다.

---

## 4. Entity 구조 및 핵심 필드

### 4.1. `Warehouse` (창고)
- `id` (BIGINT): PK
- `warehouseCode` (String): UNIQUE (예: WH-MAIN, WH-RAW)
- `warehouseName` (String): 창고 명칭 (예: 본사창고, 원재료창고)
- `status` (String): ACTIVE, INACTIVE

### 4.2. `Item` (품목)
- `id` (BIGINT): PK
- `itemCode` (String): UNIQUE (예: ITM-0001)
- `name` (String): 품목명
- `itemType` (Enum): RAW_MATERIAL, PACKAGING, WORK_IN_PROGRESS, FINISHED_GOOD, SERVICE, OTHER
- `category` (String): 카테고리 (예: 원재료, 부자재, 완제품)
- `standardCost` (BigDecimal): 표준 원가
- `unitPrice` (BigDecimal): 표준 판매 단가
- `active` (boolean): 활성화 여부

### 4.3. `Inventory` (재고)
- `id` (BIGINT): PK
- `item` (Item): FK (items.id)
- `warehouse` (Warehouse): FK (warehouses.id)
- `currentStock` (int): 실보유 물리 재고량 (현재고)
- `allocatedStock` (int): 이동/주문 등으로 잠긴 예약 재고량 (예약재고)
- `safetyStock` (int): 안전재고량
- `averageCost` (BigDecimal): 실시간 계산된 이동평균단가
- `inventoryValue` (BigDecimal): 재고자산 평가액 (`currentStock * averageCost`)

---

## 5. 정합성 및 안전 장치

- **가용재고 공식**:
  $$\text{가용재고 (Available Stock)} = \text{현재고 (Current Stock)} - \text{예약재고 (Allocated Stock)}$$
- **안전재고 하회 통제**:
  - `currentStock <= safetyStock` 조건 충족 시, 프론트엔드 목록 화면에서 경고 배지를 표기하고 대시보드의 '부족 품목 수' 지표에 실시간 반영한다.

---

## 6. 테스트 방법

### 단위 테스트 구동
```bash
cd backend
./gradlew.bat test --tests "com.dduk.service.inventory.InventoryServiceTest"
```

### 프론트 확인 방법
1. Spring Boot 서버 기동 후 `frontend/pages/inventory/list.html`을 연다.
2. 각 창고 및 품목별 현재고, 예약재고, 가용재고, 평균단가, 자산 가치가 시드 데이터 기준 정밀하게 출력되는지 확인한다.
3. 안전재고가 부족한 품목(예: `루이보스 블렌드` 등) 옆에 경고 배지가 정상 노출되는지 확인한다.

---

## 7. 변경 이력 (Change Log)

| 버전 | 일자 | 작성자 | 변경 내용 |
| :--- | :--- | :--- | :--- |
| v1.0 | 2026-05-27 | Antigravity AI | 최초 문서 작성 및 규격 통일 |
