# Inventory Dashboard

이 문서는 DDUK ERP 재고관리 도메인의 통합 재고 대시보드 시스템 설명서다. 실시간 재고량, 재고 자산 규모, 안전재고 부족 품목, 결재 대기 중인 창고 이동 내역, 최근 30일 출고량 등을 한 화면에서 파악할 수 있도록 CQRS-lite 전용 Read Model(DTO) 및 API를 설계하고 프론트 화면을 연동했다.

---

## 1. 작업 개요

재고 대시보드는 백엔드에서 대량의 재고 원장(`stock_movements`)을 실시간으로 집계하는 비효율을 방지하기 위해, CQRS-lite 패턴을 활용하여 대시보드 전용 Read Model(DTO)을 분리했다. 대시보드를 열었을 때 바로 실시간 운영 데이터가 역동적으로 표현될 수 있도록 풍부한 시드 데이터와 시계열 분산 데이터를 기본 적재했다.

---

## 2. 변경 파일 목록

- `backend/src/main/java/com/dduk/controller/inventory/InventoryDashboardController.java`
- `backend/src/main/java/com/dduk/service/inventory/InventoryQueryService.java`
- `backend/src/main/java/com/dduk/dto/inventory/InventoryDashboardResponseDto.java`
- `backend/src/main/java/com/dduk/dto/inventory/WarehouseDistributionDto.java`
- `backend/src/main/java/com/dduk/dto/inventory/RecentMovementDto.java`
- `backend/src/main/resources/db/mysql/dduk_bootstrap_data.sql`
- `frontend/pages/inventory/dashboard.html`

---

## 3. API 설명

기본 경로는 `/api/v1/inventory/dashboard`이다.

- `GET /api/v1/inventory/dashboard/stats`
  - 재고 대시보드 종합 지표 조회 (5대 KPI, 창고별 재고 분포, 최근 5건 변동 이력)

응답은 하네스 공통 규격인 `status`, `data`, `message` 구조를 사용한다.

---

## 4. KPI 카드 연산 상세 스펙

| KPI 명칭 | 연산 및 쿼리 기준 | 데이터 매핑 컬럼 | UI 스타일링 및 효과 |
| :--- | :--- | :--- | :--- |
| **총 재고 수량** | 모든 창고의 현재고량 합산 | `SUM(inventories.current_stock)` | Indigo 색상, 볼드 폰트 |
| **총 재고 자산 가치** | 모든 창고의 보유 재고 가치 합산 | `SUM(inventories.inventory_value)` | 원화 통화(₩) 기호 동반 출력 |
| **부족 품목 수** | 현재고가 안전재고 이하인 품목 개수 | `COUNT(inventories) WHERE current_stock <= safety_stock` | 경고성 Red 색상 및 아이콘 |
| **이동 대기 건수** | 결재 대기 중인 창고 이동 건수 | `COUNT(warehouse_transfers) WHERE status = 'PENDING'` | 경고/대기성 Amber 색상 배지 |
| **최근 30일 출고량** | 최근 30일 이내 발생한 출고 트랜잭션의 수량 총합 | `SUM(stock_movements.quantity) WHERE movement_type = 'OUTBOUND' AND created_at >= NOW - 30` | Gray 색상, 트렌드 증감률 표기 준비 |

---

## 5. Dashboard 집계 구조

성능 최적화와 계층 간 결합도 감소를 위해 아래와 같이 조회 모델을 분리했다.

- **`InventoryDashboardResponseDto`**: 대시보드 데이터를 단일 응답으로 전송하기 위한 통합 DTO.
- **`WarehouseDistributionDto`**: 창고별 재고 수량 및 자산 가치 정보를 담는 DTO.
- **`RecentMovementDto`**: 최근 발생한 5건의 재고 변동 내역을 보여주기 위한 요약 DTO.

모든 연산은 메모리에 대량 엔티티를 로딩하지 않고, JPA `@Query` 및 native-like 집계 쿼리를 사용하여 DB 레벨에서 `GROUP BY` 연산을 수행함으로써 빠른 응답 속도를 보장한다.

---

## 6. 프론트 구조

- HTML: `frontend/pages/inventory/dashboard.html`
- JS Services: `frontend/services/inventory/inventory-service.js`

### 주요 기능 및 UI 인터랙션
1. **5대 KPI 요약 영역**: 수치 변경 시 점진적 페이드 효과와 HARNESS 스타일 뱃지를 연동했다.
2. **창고별 재고 현황 위젯**: 창고명과 보유 수량, 보유 자산 가치를 시각적으로 구분하여 렌더링한다.
3. **결재 대기 중인 이동 요청 위젯**: 대시보드 내부에서 `PENDING` 상태의 이동 건을 즉시 확인하고 `승인` 처리할 수 있는 다이렉트 버튼 인터랙션을 제공한다.
4. **최근 재고 변동 이력 위젯**: 입고 시 Green(`+`), 출고 시 Red(`-`) 색상 처리를 적용하여 직관성을 높였다.

---

## 7. 향후 고도화 계획

- **일별 입출고 추이 차트**: 최근 7일간 일자별 총 입고량과 출고량 추이를 Chart.js 기반 Line 차트로 시각화.
- **재고-회계 자동 분개 연동**: 창고 이동 완료(`COMPLETED`) 시, 동일 트랜잭션 안에서 DRAFT 회계 전표가 자동 생성되는 도메인 트랜잭션 구현.

---

## 8. 테스트 방법

### 백엔드 컴파일 및 검증
```bash
cd backend
./gradlew.bat compileJava
```

### 프론트 확인 방법
1. Spring Boot 서버를 로컬 기동한다.
2. 웹브라우저에서 `frontend/pages/inventory/dashboard.html`을 연다.
3. KPI 카드에 실제 시드 데이터 기준 수량과 자산 가치가 노출되는지 확인한다.
4. 결재 대기 중인 이동 요청 테이블에서 [승인] 버튼을 누르고 대시보드 수치 갱신을 확인한다.

---

## 9. 변경 이력 (Change Log)

| 버전 | 일자 | 작성자 | 변경 내용 |
| :--- | :--- | :--- | :--- |
| v1.0 | 2026-05-27 | Antigravity AI | 최초 문서 작성 및 규격 통일 |
