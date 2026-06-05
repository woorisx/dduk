# [초안] 재고관리 대시보드 UI/UX 스펙서 (Inventory Dashboard Specification)

이 문서는 DDUK ERP 재고관리 대시보드의 화면 UI 구성, KPI 지표 연산 기준 및 차트 연동 스펙서이다.

* **상태**: 초안 (Draft)
* **최종 수정일**: 2026-05-27

---

## 1. 대시보드 레이아웃 구조

재고관리 대시보드는 Grid 기반 반응형 레이아웃을 준수하며, 최상단 KPI 영역, 중단 시각화(차트) 영역, 하단 실시간 모니터링(위젯) 영역으로 나뉜다.

```
+-----------------------------------------------------------------------------------+
|  [KPI CARD 1]          [KPI CARD 2]          [KPI CARD 3]          [KPI CARD 4]   |
|  총 재고 수량          총 재고 가산 자치     부족 품목 수          최근 30일 출고 |
+-----------------------------------------------------------------------------------+
|  [차트 1: 창고별 재고 자산 분포]             |  [위젯 1: 빠른 이동 작업 링크]     |
|  - 각 창고명, 보유 금액, 보유 수량           |  - 재고 현황, 이동 등록, 대시보드  |
+-----------------------------------------------------------------------------------+
|  [위젯 2: 승인 대기 중인 창고 이동 내역]     |  [위젯 3: 최근 재고 변동 이력]     |
|  - 이동 요청번호, 출발->도착, 수량, 승인버튼 |  - 최근 5건 거래 목록, 증감 수량   |
+-----------------------------------------------------------------------------------+
```

---

## 2. CQRS-lite 적용 및 Read Model 구조

대시보드 통계는 조회 빈도가 잦고 대량의 재고 원장(movements) 집계 연산이 수반될 수 있으므로, 비즈니스 엔티티와 무관한 **전용 Read Model(DTO) 및 Projection**을 구현하여 연동 속도 향상과 서비스 결합도를 제거한다.

### 2.1. `InventoryDashboardResponseDto`
대시보드 전체 데이터를 묶어 단일 응답으로 반환하는 최적의 DTO 구조이다.
* **주요 필드**:
  - `totalQuantity` (Long)
  - `totalValue` (BigDecimal)
  - `lowStockCount` (Long)
  - `outboundVolume30Days` (Long)
  - `pendingTransferCount` (Long) - 승인 대기 이동 건수
  - `warehouseDistribution` (`List<WarehouseDistributionDto>`)
  - `recentMovements` (`List<RecentMovementDto>`)

### 2.2. `DashboardStatsProjection` (또는 `WarehouseDistributionDto`)
* **필드**:
  - `warehouseName` (String)
  - `totalStock` (Long)
  - `totalValue` (BigDecimal)

---

## 3. KPI 카드 연산 상세 스펙

| KPI 명칭 | 연산 및 쿼리 기준 | 데이터 매핑 컬럼 | UI 스타일링 가이드 |
| --- | --- | --- | --- |
| **총 재고 수량** | 모든 창고의 현재고량 합산 | `SUM(inventories.current_stock)` | Indigo 텍스트, 볼드 폰트 |
| **총 재고 자산 가치** | 모든 창고의 보유 재고 가치 합산 | `SUM(inventories.inventory_value)` | 가독성 높은 원화 통화(₩) 기호 동반 |
| **부족 품목 수** | 현재고가 안전재고 이하인 품목의 개수 | `COUNT(inventories) WHERE current_stock <= safety_stock` | 경고성 Red 텍스트 |
| **최근 30일 출고량** | 최근 30일 이내에 발생한 출고(OUTBOUND) 트랜잭션의 수량 총합 | `SUM(stock_movements.quantity) WHERE type = 'OUTBOUND' AND created_at >= NOW - 30` | Gray 텍스트, 전월 대비 비율 표기 준비 |
| **이동 대기 건수** (신규) | 결재 대기 중인 창고 이동 건수 | `COUNT(warehouse_transfers) WHERE status = 'PENDING'` | Yellow 뱃지 카운트 표기 |

---

## 4. 시각화 및 차트 스펙

### 4.1. 창고별 재고 자산 분포 (Distribution Widget)
* **목적**: 창고별로 분산된 재고 자산 규모를 시각화하여 특정 창고로의 자산 쏠림 현상을 모니터링한다.
* **UI**: 
  - 좌측: 자산 규모 비율 도넛(Pie) 차트 (Chart.js 연동).
  - 우측: 창고별 리스트 카드 (창고명, 수량, 자산 평가액 원화 표시).

### 4.2. 일별 입출고 추이 차트 (Daily In/Out Trend - Phase 2 확장 준비)
* **목적**: 최근 7일간 일자별 총 입고량과 총 출고량 추이를 Bar 또는 Line 차트로 비교 분석한다.
* **UI**: 듀얼 Y축을 사용하는 Line 차트 (Green: 입고량, Red: 출고량).

---

## 5. 하단 모니터링 위젯 스펙

### 5.1. 승인 대기 창고 이동 위젯 (Pending Transfers Widget - 신규)
* **내용**: 현재 결재 대기 중인 `PENDING` 상태의 이동 요청 중 최근 5건을 표기한다.
* **동작**: 권한을 보유한 관리자 로그인 시, 대시보드 내에서 즉시 `승인` 또는 `반려` 처리할 수 있는 다이렉트 버튼 인터랙션을 제공한다.

### 5.2. 최근 재고 변동 원장 위젯 (Recent Movements Widget)
* **내용**: 가장 최근에 적재된 `StockMovement` 5건을 역순 정렬하여 보여준다.
* **요소**: 거래일시, 참조번호, 거래유형 뱃지(INBOUND/OUTBOUND/TRANSFER_OUT/TRANSFER_IN 등), 품목명, 거래수량.
