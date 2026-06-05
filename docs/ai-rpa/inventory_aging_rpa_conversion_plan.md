# 재고관리 RPA 기능을 내부 DB 기반 장기 체화 및 유통기한 임박 재고 분석으로 전환하는 구현 계획서

## 1. 목적

현재 재고관리 RPA 기능은 `INVENTORY_SHORTAGE` 태스크를 기준으로 외부 Python RPA 서버(Flask + Playwright)에 요청을 보내고, 외부 모의 벤더 포털 데이터를 크롤링한 뒤 ERP 재고와 대조하는 구조다.

이 방식의 문제는 명확하다.

- 외부 Python 서버가 항상 함께 떠 있어야 한다.
- Playwright 브라우저 실행 여부에 따라 실패 가능성이 크다.
- 개발 환경, 운영 환경, 시연 환경마다 구동 안정성이 다르다.
- 실제로 필요한 업무가 “외부 벤더 포털 대조”보다 “내부 재고 리스크 탐지”에 더 가깝다.

따라서 재고관리 RPA 기능을 다음 방향으로 전환한다.

- 외부 Python 서버 호출 제거
- ERP 내부 DB만 사용
- 백엔드(Spring Boot) 내부 비동기 작업으로 처리
- 결과는 기존처럼 `task_history + JSON 파일` 구조를 유지
- 기능 목적은 `장기 체화 재고`와 `유통기한 임박 재고` 분석 및 모의 알림 발송으로 전환

이 계획의 최종 목표는 아래다.

> "재고관리 RPA 기능을 내부 DB 기반 장기 체화 및 유통기한 임박 재고 분석으로 전환하고, 재고 담당자/영업팀이 바로 판단할 수 있는 결과를 대시보드와 공용 RPA 위젯에 안정적으로 노출한다."


## 2. 현재 구현 기준점

현재 관련 구현은 아래 파일에 걸쳐 있다.

### 백엔드

- [RpaClientService.java](C:/kmh/dduk/backend/src/main/java/com/dduk/service/admin/rpa/RpaClientService.java:23)
  - `triggerRpaTask(String taskType)`
  - `INVENTORY_SHORTAGE -> check_inventory_shortage`
  - 현재는 `rpa-server.url`로 외부 POST 요청

- [InventoryQueryService.java](C:/kmh/dduk/backend/src/main/java/com/dduk/service/inventory/InventoryQueryService.java:38)
  - `getDashboardStats()`
  - `buildInventoryShortageRpa()`
  - `task_history`에서 최근 성공 이력을 찾아 JSON 파일을 읽고 inventory 대시보드용 결과 블록을 구성

### 프론트엔드

- [floating-rpa-widget.js](C:/kmh/dduk/frontend/services/common/floating-rpa-widget.js:1)
  - 공용 AI/RPA 위젯
  - `INVENTORY_SHORTAGE` 문구, 상태 저장, polling, 결과 렌더

- [inventory-dashboard-page.js](C:/kmh/dduk/frontend/services/inventory/inventory-dashboard-page.js:1)
  - 재고관리 대시보드 중앙 RPA 카드 렌더
  - 실행 이력 조회
  - 실행 버튼 연결

### 현재 계약

- task type: `INVENTORY_SHORTAGE`
- action name: `check_inventory_shortage`
- 결과 조회 endpoint: `GET /api/v1/inventory/dashboard/stats`
- 실행 endpoint: `POST /api/v1/admin/rpa/trigger`
- 작업 상세 endpoint: `GET /api/v1/admin/tasks/{taskId}`
- 대시보드 결과 블록 계약:
  - `status`
  - `message`
  - `items`
  - `latestTaskId`
  - `latestCollectedAt`


## 3. 전환 방향 요약

전환 방향은 아래처럼 고정한다.

### 유지하는 것

- `taskType = INVENTORY_SHORTAGE`
- `actionName = check_inventory_shortage`
- `/api/v1/admin/rpa/trigger` 진입점
- `task_history` 사용
- 결과 JSON 파일 저장 방식
- `/api/v1/inventory/dashboard/stats` 안의 `inventoryShortageRpa` 블록 계약

### 바꾸는 것

- 외부 Python RPA 서버 호출 제거
- 외부 벤더 크롤링 제거
- “재고 부족 대조” 문구 제거
- “장기 체화 및 유통기한 임박 재고 분석” 시나리오로 교체
- 결과 `items`의 의미를 `부족재고`가 아니라 `재고 리스크 항목`으로 전환


## 4. 사용자 검토/사전 합의 필요 사항

이 계획은 바로 구현 가능한 수준으로 작성하지만, 아래는 선행 합의가 필요하다.

### 4.1 외부 Python 서버 제거 범위

이번 변경은 `INVENTORY_SHORTAGE`에만 적용한다.

- `PURCHASE_PRICE`: 기존 외부 Python 연동 유지
- `HR_MIN_WAGE`: 기존 외부 Python 연동 유지
- `INVENTORY_SHORTAGE`: 백엔드 내부 분석형으로 전환

즉, `RpaClientService` 전체를 없애는 것이 아니라 `INVENTORY_SHORTAGE`만 내부 분기한다.

### 4.2 UI 한글 카피 변경

기존 문구:

- 재고 부족 점검
- 부족 재고 조회
- 부족 재고 감지 결과

변경 문구:

- 장기 체화 및 임박 재고 분석
- 장기 미출고 및 유통기한 임박 품목 분석
- 재고 리스크 분석 결과

### 4.3 “90일 이상 미판매” 정의

이 문서에서는 1차 구현 기준을 아래로 고정한다.

- 기본 규칙: `최근 OUTBOUND 또는 판매성 stock movement가 90일 이상 없는 재고`
- 보조 규칙: movement 자체가 90일 이상 없으면 체화 후보로 간주

즉 “입고 후 90일”이 아니라 “최근 출고/판매 기준 90일 무변동”에 더 가깝게 잡는다.

### 4.4 유통기한 데이터 정확도

현재 inventory 도메인 기준으로는 다음이 확인됐다.

- [Item.java](C:/kmh/dduk/backend/src/main/java/com/dduk/entity/inventory/Item.java:1) 에는 `expiryDate` 필드가 없다.
- [Inventory.java](C:/kmh/dduk/backend/src/main/java/com/dduk/entity/inventory/Inventory.java:1) 에도 유통기한 필드가 없다.
- 현재 `items`, `inventories` 주 경로 기준으로는 유통기한 임박 분석을 바로 정확하게 구현할 수 없다.

따라서 유통기한 분석은 아래 두 단계로 나눈다.

- 1차: 체화 재고 분석 우선 구현
- 2차: `expiry_date`를 inventory 도메인에 실제 연결한 뒤 임박 재고 분석 추가

문서에서는 유통기한까지 포함한 최종 구조를 제안하되, 구현 순서는 `체화 -> 유통기한` 순으로 가져간다.


## 5. 구현 목표

최종 기능 목표는 다음과 같다.

1. 재고관리 대시보드에서 RPA 실행 버튼을 누른다.
2. 백엔드는 외부 RPA 서버를 호출하지 않고 내부 DB 분석 작업을 비동기로 수행한다.
3. 장기 체화 재고와 유통기한 임박 재고를 선별한다.
4. 선별 결과를 JSON 파일로 `rpa/outputs/`에 저장한다.
5. `task_history`를 `SUCCESS` 또는 `FAILED`로 갱신한다.
6. inventory 대시보드와 공용 RPA 위젯이 그 JSON을 읽어 결과를 출력한다.
7. 모의 알림 내용(슬랙/이메일용 요약)을 결과 파일 및 로그에 포함한다.


## 6. 기능 범위 정의

### 6.1 1차 구현 범위

이번 문서 기준으로 실제 바로 구현할 1차 범위는 아래다.

- `INVENTORY_SHORTAGE` 내부 분석형 전환
- 장기 체화 재고 탐지
- 모의 알림 메시지 생성
- JSON 결과 저장
- `task_history` 상태 반영
- 재고 대시보드와 공용 RPA 위젯 텍스트/테이블 전환

### 6.2 2차 구현 범위

아래는 후속 단계로 분리한다.

- 유통기한 컬럼/배치 스키마 보강
- 실제 임박 재고 탐지
- 실제 슬랙 webhook 연동
- 실제 SMTP 발송 연동
- 임계값 설정화
- 주기 배치 실행


## 7. 백엔드 변경 계획

## 7.1 `RpaClientService.java` 수정

대상:

- [RpaClientService.java](C:/kmh/dduk/backend/src/main/java/com/dduk/service/admin/rpa/RpaClientService.java:23)

현재 상태:

- 모든 RPA taskType이 외부 `rpa-server.url`로 POST된다.

변경 목표:

- `INVENTORY_SHORTAGE`일 때만 외부 호출 대신 내부 서비스 실행

권장 구현 방식:

- `triggerRpaTask(String taskType)` 안에서 `normalizedTaskType` 분기
- `INVENTORY_SHORTAGE`면 `InventoryAgingAnalysisService` 호출
- 이 호출은 raw thread가 아니라 Spring 비동기 방식 사용

권장 구조:

```java
if (TASK_TYPE_INVENTORY_SHORTAGE.equals(normalizedTaskType)) {
    taskHistoryService.createRpaTriggerRequest(taskId, actionName, requestBody);
    taskHistoryService.markRpaTaskAccepted(taskId);
    inventoryAgingAnalysisService.runAsync(taskId, normalizedTaskType, actionName);
    return accepted payload;
}
```

세부 요구사항:

- 기존 `taskId` 생성 규칙 유지
- 기존 `taskHistoryService.createRpaTriggerRequest(...)` 유지
- 기존 응답 구조 유지
  - `taskId`
  - `taskType`
  - `actionName`
  - `accepted`

예외 처리:

- 내부 비동기 작업 enqueue 실패 시 즉시 trigger failure 처리
- 실행 중 예외는 비동기 서비스 안에서 잡아 `task_history`를 `FAILED`로 닫음


## 7.2 신규 서비스 `InventoryAgingAnalysisService.java` 추가

신규 파일:

- `backend/src/main/java/com/dduk/service/inventory/InventoryAgingAnalysisService.java`

역할:

- 내부 DB 기반 재고 리스크 분석 전담
- 결과 JSON 저장
- 모의 알림 내용 생성
- `task_history` 완료/실패 반영

권장 메서드:

- `runAsync(String taskId, String taskType, String actionName)`
- `runAnalysis(...)`
- `findAgingItems(...)`
- `findExpiryRiskItems(...)`
- `buildSummary(...)`
- `writeResultFile(...)`
- `markSuccess(...)`
- `markFailure(...)`

권장 의존성:

- `InventoryRepository`
- `StockMovementRepository`
- `TaskHistoryService`
- `ObjectMapper`
- 필요 시 `EntityManager` 또는 전용 query repository

### 7.2.1 장기 체화 재고 분석 규칙

1차 규칙:

- 재고 수량이 0보다 큼
- 최근 90일 이내 OUTBOUND/판매성 movement 없음
- 또는 최근 movement 자체가 90일 이상 없음

권장 출력 필드:

- `itemId`
- `itemName`
- `warehouseName`
- `availableStock`
- `lastMovementAt`
- `daysWithoutOutbound`
- `statusType = AGING`
- `statusLabel = 장기 체화`
- `recommendedAction`

추천 액션 예시:

- 할인/프로모션 검토
- 묶음 판매 전환
- 구매 중단 검토
- 우선 출고 대상 지정

### 7.2.2 유통기한 임박 재고 분석 규칙

중요:

현재 inventory 엔티티 기준으로 유통기한 필드가 직접 연결돼 있지 않다.

따라서 문서상 구현 전략은 두 갈래다.

#### A안. 1차 즉시 구현용

- 유통기한 분석 비활성
- 결과 summary에는 `expirySupport = false`를 남김
- 메시지에 “유통기한 데이터 스키마 연결 전이라 체화 재고만 분석했다”를 넣음

#### B안. 스키마 보강 포함 구현용

신규 스키마 추가:

- `inventory_batches`
  - `id`
  - `inventory_id`
  - `item_id`
  - `warehouse_id`
  - `lot_no`
  - `expiry_date`
  - `quantity`
  - `created_at`
  - `updated_at`

또는 최소안:

- `inventories.expiry_date`

권장안:

- 실무 정확도 때문에 `inventory_batches`가 더 적절
- 하지만 빠른 구현이 목표면 `inventories.expiry_date`가 1차 데모용으로는 단순함

임박 기준:

- `expiry_date <= today + 30 days`
- 수량 > 0

권장 출력 필드:

- `itemId`
- `itemName`
- `warehouseName`
- `availableStock`
- `expiryDate`
- `daysToExpiry`
- `statusType = EXPIRY`
- `statusLabel = 유통기한 임박`
- `recommendedAction`

추천 액션 예시:

- 우선 출고 지정
- 판촉/할인 전환
- 폐기 예정 검토


## 7.3 결과 JSON 구조

저장 위치:

- `rpa/outputs/inventory_aging_{taskId}.json`

권장 파일명 예시:

- `rpa/outputs/inventory_aging_rpa-task-a1b2c3d4.json`

권장 JSON 구조:

```json
{
  "taskId": "rpa-task-a1b2c3d4",
  "taskType": "INVENTORY_SHORTAGE",
  "actionName": "check_inventory_shortage",
  "generatedAt": "2026-06-02T17:00:00",
  "summary": {
    "agingCount": 4,
    "expiryCount": 0,
    "totalRiskCount": 4,
    "expirySupport": false
  },
  "notifications": {
    "slackPreview": "장기 체화 4건이 탐지되었습니다.",
    "emailPreview": "주간 재고 리스크 분석 결과 장기 체화 4건이 있습니다."
  },
  "items": [
    {
      "itemId": 12,
      "itemName": "아망티 얼그레이 클래식 삼각티백 (20입)",
      "warehouseName": "완제품 창고",
      "availableStock": 18,
      "statusType": "AGING",
      "statusLabel": "장기 체화",
      "lastMovementAt": "2026-02-01T11:00:00",
      "daysWithoutOutbound": 121,
      "expiryDate": null,
      "daysToExpiry": null,
      "recommendedAction": "할인 판매 또는 우선 출고 검토"
    }
  ]
}
```

핵심 원칙:

- 기존 프론트가 읽는 `status/message/items`는 대시보드 블록에서 계속 유지
- 파일 내부는 summary/notifications를 넉넉히 담아도 됨


## 7.4 `task_history` 갱신 규칙

성공 시:

- 상태 `SUCCESS`
- `responsePayload.data.filePath`에 결과 파일 경로 저장
- 요약 카운트도 payload에 함께 기록 가능

실패 시:

- 상태 `FAILED`
- 에러 코드와 메시지 저장

권장 성공 payload 예시:

```json
{
  "taskId": "rpa-task-a1b2c3d4",
  "taskType": "INVENTORY_SHORTAGE",
  "actionName": "check_inventory_shortage",
  "status": "success",
  "data": {
    "filePath": "rpa/outputs/inventory_aging_rpa-task-a1b2c3d4.json",
    "agingCount": 4,
    "expiryCount": 0,
    "totalRiskCount": 4
  }
}
```


## 7.5 `InventoryQueryService.java` 수정

대상:

- [InventoryQueryService.java](C:/kmh/dduk/backend/src/main/java/com/dduk/service/inventory/InventoryQueryService.java:138)

현재 상태:

- `buildInventoryShortageRpa()`가 외부 alert JSON을 읽고 ERP low stock과 merge한다.

변경 원칙:

- 메서드명은 1차에 그대로 유지해도 된다.
- 중요한 건 반환 계약이다.

즉:

- 메서드명을 꼭 `buildInventoryAgingRpa()`로 바꾸지 않아도 됨
- 1차 구현은 내부 구현과 메시지/필드 의미만 바꿔도 충분

권장 변경 내용:

- 최근 성공 task의 결과 JSON을 읽는다.
- `summary`와 `items`를 기반으로 dashboard block을 구성한다.
- 기존 block 구조를 유지하되 의미를 아래처럼 바꾼다.

권장 결과 block 계약:

- `taskType`
- `actionName`
- `available`
- `status`
- `vendorName`
- `message`
- `latestTaskId`
- `latestCollectedAt`
- `agingCount`
- `expiryCount`
- `totalRiskCount`
- `items`

`items` 각 row 권장 필드:

- `itemName`
- `warehouseName`
- `availableStock`
- `statusType`
- `statusLabel`
- `lastMovementAt`
- `daysWithoutOutbound`
- `expiryDate`
- `daysToExpiry`
- `recommendedAction`

상태값 권장:

- `EMPTY`
- `READY`
- `EMPTY_RESULT`
- `MISSING_FILE`
- `ANALYSIS_PARTIAL`

메시지 예시:

- `장기 체화 재고 4건을 탐지했어.`
- `장기 체화 재고 4건을 탐지했고, 유통기한 데이터는 아직 연결되지 않았어.`
- `분석을 완료했지만 노출할 리스크 항목은 없었어.`


## 8. 프론트엔드 변경 계획

## 8.1 `floating-rpa-widget.js` 수정

대상:

- [floating-rpa-widget.js](C:/kmh/dduk/frontend/services/common/floating-rpa-widget.js:1)

현재 상태:

- `INVENTORY_SHORTAGE`를 “재고 부족 점검” 문맥으로 렌더한다.

변경 목표:

- 공용 위젯 카피를 새 시나리오로 변경
- 결과 테이블을 체화/임박 재고 중심으로 변경

수정 포인트:

### 텍스트 변경

- 제목:
  - 기존: `재고 부족 점검`
  - 변경: `장기 체화 및 임박 재고 분석`

- 설명:
  - 기존 부족재고 중심 설명 제거
  - 변경: `장기 미출고 제품과 유통기한 임박 원자재를 분석하고 알림을 보냅니다.`

- 상태 라벨:
  - `READY -> 분석 완료`
  - `EMPTY_RESULT -> 리스크 없음`
  - `MISSING_FILE -> 결과 누락`

### 결과 카드 요약 변경

기존 재고부족 카드 대신 아래 요약을 노출한다.

- 장기 체화 건수
- 유통기한 임박 건수
- 총 리스크 건수

### 결과 리스트 컬럼 변경

권장 컬럼:

- 품목명
- 창고
- 재고량
- 상태
- 세부 기준
- 조치 제안

세부 기준 예시:

- `최근 출고 121일 없음`
- `유통기한 12일 남음`


## 8.2 `inventory-dashboard-page.js` 수정

대상:

- [inventory-dashboard-page.js](C:/kmh/dduk/frontend/services/inventory/inventory-dashboard-page.js:1)

현재 상태:

- `renderShortageRpa(block)`가 부족재고/경고 중심 row를 렌더한다.

변경 목표:

- 대시보드 중앙 RPA 카드가 새 JSON 구조에 맞게 렌더되도록 전환

수정 포인트:

### `rpaChipMeta` 수정

기존:

- 부족재고 조회 문맥

변경:

- `READY -> 결과 확인 가능`
- `EMPTY_RESULT -> 리스크 없음`
- `ANALYSIS_PARTIAL -> 부분 분석`
- `MISSING_FILE -> 결과 누락`

### `renderShortageRpa(block)` 수정

기존 출력:

- available/safety/rpa stock status 중심

변경 출력:

- `itemName`
- `warehouseName`
- `availableStock`
- `statusLabel`
- `recommendedAction`

추가 표시:

- `agingCount`
- `expiryCount`
- `totalRiskCount`

### 페이지 메시지 수정

- 기존: 부족재고 조회/점검 문구
- 변경: 장기 체화 및 임박 재고 분석 문구

### 실행 이력 UI

action name은 그대로 `check_inventory_shortage`를 유지해도 된다.
문구만 새 기능으로 바꾼다.


## 9. 데이터 규칙 상세

## 9.1 체화 재고 판정 규칙

권장 SQL/쿼리 기준:

- 현재 재고 수량 > 0
- 최근 OUTBOUND movement가 90일보다 오래됨 또는 없음
- movement가 아예 없는 경우 `inventory.updatedAt` 기준 보조 판단 가능

우선순위:

1. `stock_movements.created_at`
2. `movement_type = OUTBOUND`
3. 없으면 `inventories.updated_at`

주의:

- 단순히 `inventories.updated_at`만 보면 재고조정/할당 변경도 잡힐 수 있음
- 가능하면 movement 기준이 우선


## 9.2 유통기한 임박 판정 규칙

현실적 권고:

- 1차 구현에는 “유통기한 분석은 스키마 미연결 시 비활성”으로 처리
- 임박 분석을 반드시 같이 넣고 싶다면 스키마를 먼저 보강

1차 fallback 메시지 예시:

- `유통기한 데이터가 아직 재고 도메인에 연결되지 않아 체화 재고만 분석했어.`


## 9.3 추천 액션 규칙

상태별 추천 액션 예시:

- `AGING`
  - 할인 판매 검토
  - 우선 출고 대상 지정
  - 묶음 판매/행사 전환
  - 추가 구매 중단 검토

- `EXPIRY`
  - 유통기한 임박 우선 출고
  - 영업팀 긴급 프로모션 전달
  - 폐기/반품 검토


## 10. 모의 알림 설계

이번 문서 기준 1차 구현은 “실제 외부 발송”이 아니라 “모의 알림 생성”까지로 제한한다.

### 슬랙 모의 알림

로그 또는 결과 JSON에 preview 저장:

```text
[재고 리스크 주간 알림]
- 장기 체화: 4건
- 유통기한 임박: 0건
- 우선 확인 품목: 아망티 얼그레이 클래식 삼각티백 (20입), 아망티 내열유리 티포트 (600ml)
```

### 이메일 모의 알림

로그 또는 결과 JSON에 preview 저장:

```text
제목: [DDUK ERP] 주간 재고 리스크 알림
본문: 장기 체화 재고 4건이 탐지되었습니다. 상세는 대시보드에서 확인하세요.
```

권장 구현:

- 결과 JSON에 `notifications.slackPreview`, `notifications.emailPreview` 저장
- 서버 로그에도 같이 남김


## 11. 구현 순서

권장 구현 순서는 아래다.

### Step 1

- `InventoryAgingAnalysisService` 추가
- 체화 재고 분석만 우선 구현

### Step 2

- `RpaClientService`에서 `INVENTORY_SHORTAGE` 내부 분기 추가
- task history accepted/success/failure 흐름 연결

### Step 3

- 결과 JSON 저장 구현
- `InventoryQueryService`가 새 JSON 구조를 읽도록 수정

### Step 4

- `inventory-dashboard-page.js` 렌더 변경
- `floating-rpa-widget.js` 렌더 변경

### Step 5

- 유통기한 데이터 미연결 상태 메시지 추가
- 모의 알림 preview 표시 완성

### Step 6

- 필요 시 inventory expiry 스키마 보강 후 2차 확장


## 12. 검증 계획

## 12.1 자동 검증

백엔드:

- `.\gradlew.bat compileJava`

프론트엔드:

- `node --check frontend/services/common/floating-rpa-widget.js`
- `node --check frontend/services/inventory/inventory-dashboard-page.js`


## 12.2 수동 검증

1. 재고관리 대시보드 접속
2. 우측 공용 RPA 제어 위젯에서 새 문구 확인
3. `RPA 실행` 클릭
4. 상태가 `실행 중`으로 변경되는지 확인
5. 수 초 내 `성공` 또는 `결과 확인 가능`으로 전환되는지 확인
6. `task_history`에 성공 이력이 생성되는지 확인
7. `rpa/outputs/inventory_aging_*.json` 파일 생성 확인
8. 대시보드 중앙 카드에 체화/임박 카운트와 항목 목록이 뜨는지 확인

주의:

- “항상 1~2초 보장”으로 문서화하지 않는다.
- 소규모 demo DB 기준 수 초 내 완료를 목표로 한다.


## 13. 실패 시 대응 규칙

### 분석 대상 없음

- 상태: `EMPTY_RESULT`
- 메시지: `분석을 완료했지만 장기 체화 또는 임박 재고는 없었어.`

### 결과 파일 저장 실패

- 상태: `FAILED`
- `task_history.errorMessage` 기록

### 유통기한 데이터 미연결

- 상태: `ANALYSIS_PARTIAL`
- 메시지: `체화 재고 분석은 완료했지만 유통기한 데이터는 아직 연결되지 않았어.`


## 14. 비고

이 계획은 현재 코드 구조를 최대한 유지하면서 inventory RPA만 내부 분석형으로 전환하는 데 초점을 둔다.

핵심 철학은 다음과 같다.

- 외부 크롤링 의존 제거
- 백엔드 단독 실행
- 기존 task/history/result 계약 최대 유지
- 프론트는 계약 호환 범위 안에서 카피와 컬럼만 전환

즉, 이번 작업은 “RPA 시스템을 새로 만드는 것”이 아니라:

> `INVENTORY_SHORTAGE`라는 기존 RPA 슬롯에 더 실용적이고 더 안정적인 내부 재고 리스크 분석 기능을 넣는 전환 작업이다.

