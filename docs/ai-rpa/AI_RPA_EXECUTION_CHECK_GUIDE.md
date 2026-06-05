# DDUK ERP AI/RPA 실행 체크 가이드

## 목적

- 현재 프로젝트에 실제로 붙어 있는 AI/RPA 기능을 실행하고 확인하는 기준 문서다.
- 예전 목업 시연 흐름이 아니라 현재 코드 기준 동작만 적는다.
- 이 문서는 로컬 실행, 상태 확인, 화면별 체크 포인트에 집중한다.

## 현재 기준 범위

- RPA Stage 1: 공통 트리거/콜백/이력 적재
- RPA Stage 2: 구매/발주 대시보드 공개 아망티 수집
- RPA Stage 3: 재고 부족 조회형 시나리오
- RPA Stage 5: 인사/급여 기준정보 조회형 시나리오
- RPA Stage 6: 최근 실행 이력/재시도 UX
- AI Phase 2: 규칙 기반 이상 탐지
- AI Phase 3: 통계 기반 추천 발주

제외 범위:

- 회계 대시보드 RPA
- 운영 자동 확정/자동 발주
- 프로덕션 외부 배포 절차
- 고도화 ML 모델 운영

## 실행 전 확인

- 프로젝트 루트에 `.env`가 있어야 한다.
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `RPA_CALLBACK_TOKEN` 값이 맞아야 한다.
- AI 기능 확인이 필요하면 `GEMINI_API_KEY`도 설정한다.
- RPA 공개 수집은 현재 아망티 공개 페이지 기준으로 동작한다.
- AI/RPA 더미 실행 결과는 제거된 상태라 실제 실행 이력이 없으면 화면이 비어 있을 수 있다.

## 기본 기동

### 권장 포트

- frontend: `http://localhost:5500`
- backend: `http://localhost:8080`
- ai-server: `http://localhost:5000`
- rpa-server: `http://localhost:5050`

### 빠른 기동 순서

1. `ai-all-start.bat` 실행
2. backend health 성격 API 또는 로그인 페이지 진입 확인
3. `http://localhost:5000/health` 확인
4. `http://localhost:5050/health` 확인
5. `http://localhost:5500` 진입 확인

## 공통 상태 확인

### 서버 확인

- AI health: `http://localhost:5000/health`
- RPA health: `http://localhost:5050/health`
- 응답은 `{"status":"UP"}` 형태면 된다.

### 로그 확인

- 최신 실행 로그는 `.local-run/` 아래 타임스탬프 폴더에 쌓인다.
- 우선 확인할 로그:
  - `backend.out.log`
  - `backend.err.log`
  - `ai-server.out.log`
  - `ai-server.err.log`
  - `rpa-server.out.log`
  - `rpa-server.err.log`

### 실행 결과 파일 확인

- RPA 결과 파일은 `rpa/outputs/` 아래에 생긴다.
- 현재는 시드 파일이 아니라 실제 실행 파일만 남는다.
- 예시:
  - `orders_rpa-task-*.json`
  - `inventory_alerts_rpa-task-*.json`

## 권한 체크

### 관리자 계정

- `system-admin`
- `task-history`
- `anomaly-detection`
- AI/RPA 운영 카드 노출

### 재고 계정

- `inventory/dashboard`
- `inventory/purchase-dashboard`
- `inventory/reorder`
- 관리자 전용 AI/RPA 운영 메뉴 비노출 또는 차단

### 인사 계정

- `hr/payroll/list`
- 관리자 전용 운영 메뉴 차단

## 화면별 실행 체크

### 1. 공통 RPA 실행 이력

확인 위치:

- 관리자 시스템 페이지
- task history 페이지
- 각 도메인 카드의 최근 실행 이력

체크 포인트:

- 실행 요청 후 `RUNNING` 상태가 보이는지
- 완료 후 `SUCCESS` 또는 `FAILED`로 바뀌는지
- `taskType`, `actionName`이 섞이지 않는지
- 실패 시 에러 메시지가 최근 이력에서 확인되는지

### 2. 구매/발주 대시보드 RPA

시나리오:

- 공개 아망티 페이지에서 구매 검토용 데이터를 수집한다.
- 로그인 없는 공개 페이지 기준이다.

체크 포인트:

- `inventory/purchase-dashboard` 진입
- 실행 버튼 동작
- 최근 수집 결과 카드 반영
- `productName`, `unitPrice`, `spec`, `vendorName` 표시
- 공개 수집 결과가 `rpa/outputs/orders_*.json`에 저장되는지
- task history에 해당 실행 이력이 남는지

주의:

- 현재는 공개 페이지 수집이라 회원 전용 데이터는 나오지 않는다.
- 팝업/페이지 구조 변경 시 일부 수집 필드 품질이 달라질 수 있다.

### 3. 재고 대시보드 부족 조회

시나리오:

- 재고 부족 또는 보충 필요 상태를 조회형 RPA 카드로 보여준다.

체크 포인트:

- `inventory/dashboard` 진입
- 부족 조회 실행 버튼 동작
- 경고 카드 표시
- 최근 실행 이력 반영
- 결과 파일이 `rpa/outputs/inventory_alerts_*.json`으로 저장되는지

### 4. 인사/급여 기준정보 조회

시나리오:

- 급여 페이지에서 외부 기준정보 조회 결과를 보조 카드로 확인한다.

체크 포인트:

- `hr/payroll/list` 진입
- 기준정보 조회 실행 가능 여부
- 최신 결과 카드 반영
- 최근 실행 이력 반영

### 5. 이상 탐지

확인 위치:

- `admin/anomaly-detection`

체크 포인트:

- 목록 조회 성공
- refresh 동작
- 상태 전환 가능:
  - `OPEN`
  - `CONFIRMED`
  - `FALSE_POSITIVE`
  - `IGNORED`
- 인벤토리/추천발주 조건과 맞는 경고가 생성되는지

### 6. 추천 발주

확인 위치:

- `inventory/reorder`

체크 포인트:

- 목록 조회 성공
- 상태별 구분 표시:
  - `READY`
  - `REVIEW`
  - `DISABLED`
- 추천 근거 노출
- `availableStock` 기준 계산이 맞는지
- fallback 메시지가 비정상적으로 비어 있지 않은지

## 현재 프로젝트 기준 주의사항

- AI/RPA 더미 실행 결과는 제거됐다.
- 따라서 실행 이력, 결과 카드, 결과 파일은 실제 실행 후에만 생긴다.
- 구매 RPA는 현재 목업 포털이 아니라 공개 아망티 페이지 기준이다.
- task history는 `taskType=RPA/AI`와 `actionName` 기준으로 봐야 한다.

## 문제 발생 시 우선 확인

### RPA가 실행되지 않을 때

- `RPA_CALLBACK_TOKEN` 일치 여부
- `rpa-server` health
- `rpa/venv` 의존성 설치 상태
- `rpa-server.err.log`

### AI 기능이 비정상일 때

- `GEMINI_API_KEY`
- `ai-server` health
- `ai-server.err.log`

### 화면은 뜨는데 결과가 없을 때

- 실제 실행을 했는지
- task history에 최근 row가 생겼는지
- `rpa/outputs/` 결과 파일이 생성됐는지
- 권한 차단으로 API가 막히지 않았는지

## 최소 완료 기준

- 관리자/재고/인사 계정 권한이 의도대로 보인다.
- 구매 RPA 1건 이상 성공 이력이 남는다.
- 재고 부족 조회 1건 이상 성공 이력이 남는다.
- 인사 기준정보 조회 카드가 정상 반영된다.
- 이상 탐지 목록과 상태 전환이 동작한다.
- 추천 발주 페이지가 상태/근거를 정상 표시한다.
