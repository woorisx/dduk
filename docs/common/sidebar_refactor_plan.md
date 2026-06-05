# sidebar.js 4분할 실행 계획서

## 1. 문서 목적

이 문서는 `frontend/services/common/sidebar.js`를 과도하게 잘게 쪼개지 않고, 실제로 바로 구현 가능한 `4분할 구조`로 정리하기 위한 실행 계획서다.

이번 계획의 목표는 두 가지다.

1. `sidebar.js`의 과도한 책임 집중을 줄인다.
2. 한글 인코딩 깨짐 복구 범위를 기능 단위로 고립시킨다.

이번 문서는 설계 미학보다 `현실적으로 안전하고 구현 가능한 구조`를 우선한다.


## 2. 현재 문제 정의

현재 [sidebar.js](C:/kmh/dduk/frontend/services/common/sidebar.js:1)는 약 `1895줄`이며, 아래 기능이 한 파일에 섞여 있다.

- 좌측 사이드바 메뉴 렌더링
- 최근 사용 메뉴 저장/복원
- 모바일 반응형 처리
- 플로팅 AI 포털 DOM 생성
- AI 챗봇 메시지 전송/이력
- 이상 감지/예측 분석 카드 렌더링
- RPA 제어 위젯 상태 저장/복원/폴링/결과 렌더링
- 플로팅 위젯 드래그/리사이즈
- 대량의 inline CSS와 HTML 템플릿 문자열

이 구조의 문제는 아래와 같다.

- 한 기능 수정이 다른 기능을 쉽게 건드린다.
- 공용 함수 누락 시 런타임 오류가 공통 영역에서 터진다.
- 한글 깨짐이 생기면 복구 범위가 파일 전체로 퍼진다.
- 코드 리뷰와 추적 비용이 지나치게 높다.


## 3. 최종 목표 구조

이번 분리의 최종 목표는 아래 `4파일`이다.

```text
frontend/services/common/
  sidebar.js
  sidebar-menu.js
  floating-portal.js
  floating-rpa-widget.js
```

이 이상 잘게 쪼개는 것은 이번 단계에서 하지 않는다.


## 4. 파일별 책임

### 4.1 `sidebar.js`

역할:

- 진입점
- 초기화 순서 제어
- 공용 유틸 보관
- 기존 전역 함수 브리지 유지

이 파일에 남겨둘 것:

- `escapeHtml`
- `resolveHref`
- `getRootPath`
- `formatDateTime`
- 초기 `DOMContentLoaded` 또는 즉시 초기화 제어
- `toggleSidebar`, `toggleMenu`, `toggleRecentMenu`, `toggleFloatingChatbot` 같은 전역 브리지 연결

이 파일에 남기지 않을 것:

- 챗봇 API 호출 상세
- anomaly/prediction 렌더 상세
- RPA 상태 저장/폴링/결과 렌더
- 드래그/리사이즈 세부 구현

목표:

- 최종적으로 `sidebar.js`는 얇은 오케스트레이터 역할만 하도록 줄인다.


### 4.2 `sidebar-menu.js`

역할:

- 좌측 메뉴 그룹 정의
- 메뉴 HTML 렌더링
- 현재 페이지 active 처리
- 메뉴 열기/닫기
- 최근 사용 메뉴 저장/복원
- 모바일 반응형 메뉴 동작

여기로 이동할 것:

- `MENU_GROUPS`
- `renderMenuItem`
- `renderGroups`
- `renderSidebar`
- `updateMenuIcon`
- `toggleMenu`
- `toggleRecentMenu`
- `toggleSidebar`
- `readRecentMenus`
- `writeRecentMenus`
- `addRecentMenu`
- `renderRecentMenus`
- `checkMobile`
- `openCurrentMenuGroup`
- `initSidebar`

이 파일의 성격:

- 순수 메뉴/내비게이션 전용
- AI 포털과 RPA 로직을 절대 포함하지 않음


### 4.3 `floating-portal.js`

역할:

- AI 플로팅 포털 셸 전체
- 포털 DOM 생성
- 포털 CSS 주입
- 탭 전환
- 챗봇 UI
- anomaly/prediction UI
- 트리거 버튼 드래그
- 패널 리사이즈

여기로 이동할 것:

- `alignPanelToTrigger`
- `toggleFloatingChatbot`
- `openFloatingChatbotWithQuery`
- `switchPortalTab`
- `sendFloatingChatMessage`
- `appendFloatingMessage`
- `renderAnomalyList`
- `renderPredictionList`
- `initAICopilotPortal`
- 드래그 관련 로직
- 리사이즈 관련 로직
- 포털 HTML/CSS 템플릿 문자열

이 파일의 성격:

- AI 포털 UI 전체 전담
- 단, RPA 탭의 실제 상태 로직은 여기서 하지 않음

포털 안에서 담당할 범위:

- `chatbot` 탭 UI와 API 호출
- `anomaly` 탭 UI와 API 호출
- `prediction` 탭 UI와 계산 로직
- `rpa` 탭 컨테이너 마운트까지만 담당


### 4.4 `floating-rpa-widget.js`

역할:

- 공용 RPA 위젯의 상태 저장
- 상태 복원
- task polling
- 결과 렌더링
- 페이지 컨텍스트별 `taskType` 판정

여기로 이동할 것:

- `RPA_STATE_STORAGE_KEY`
- `translateRpaType`
- `translateRpaAction`
- `getCurrentRole`
- `getApiBaseUrl`
- `getHeaders`
- `loadRpaStates`
- `persistRpaStates`
- `saveRpaState`
- `clearRpaState`
- `getStoredRpaState`
- `canRenderRpaResult`
- `toPersistedDetail`
- `requestRpaApi`
- `getRpaContext`
- `restoreRpaStateForCurrentContext`
- `renderDynamicRpaWidget`
- `renderStatus`
- `setMessage`
- `updateSummary`
- `stopPolling`
- `loadTaskDetail`
- `pollTask`
- `triggerRpa`
- `checkAndRenderPreExistingResult`
- `fetchAndRenderRpaResult`

이 파일의 성격:

- 저장/복원/폴링/요약 렌더 같은 상태성 높은 코드 전담
- 다른 탭 로직과 섞지 않음


## 5. 왜 4분할이 적당한가

이번 계획에서 `4분할`을 권장하는 이유는 아래와 같다.

### 5.1 너무 적게 나누면

- `sidebar.js`가 다시 비대해진다.
- AI 포털과 RPA 위젯 회귀가 계속 같이 난다.

### 5.2 너무 많이 나누면

- 파일 수만 늘고 탐색 비용이 커진다.
- 분리 이득보다 import/전역 브리지 관리 비용이 커진다.
- 지금 팀 컨텍스트에서 추적성이 오히려 나빠질 수 있다.

### 5.3 4분할은 균형점이다

- 메뉴와 포털을 분리할 수 있다.
- 포털과 RPA를 분리할 수 있다.
- 진입점은 얇게 유지할 수 있다.
- 구현 순서를 단계적으로 가져가기 쉽다.


## 6. 분리 원칙

### 6.1 한 번에 전면 재작성하지 않는다

기존 `sidebar.js`를 지우고 새 구조로 한 번에 바꾸지 않는다.

대신:

1. 새 파일을 만든다.
2. 기존 로직을 기능 단위로 옮긴다.
3. `sidebar.js`에서 브리지 호출로 대체한다.
4. 각 단계마다 검증한다.


### 6.2 기존 전역 계약은 유지한다

현재 HTML이나 템플릿에서 기대할 가능성이 있는 전역 함수는 바로 없애지 않는다.

유지 대상:

- `toggleSidebar`
- `toggleMenu`
- `toggleRecentMenu`
- `toggleFloatingChatbot`
- `openFloatingChatbotWithQuery`

원칙:

- 전역 이름은 유지
- 내부 구현만 분리 파일로 위임


### 6.3 공용 유틸은 `sidebar.js`에 남긴다

이번 단계에서는 별도 `shared.js`를 만들지 않는다.

이유:

- 공용 유틸 파일까지 추가하면 분리가 다시 과분해된다.
- 현재 필요한 건 “대형 파일 감량”이지 “모든 헬퍼를 독립 모듈화”가 아니다.

따라서 아래는 `sidebar.js`에 유지한다.

- `escapeHtml`
- `resolveHref`
- `getRootPath`
- `formatDateTime`


### 6.4 인코딩 복구는 옮기는 구간에서 같이 한다

한글 깨짐 복구는 문서 전체 치환 방식으로 하지 않는다.

원칙:

- `sidebar-menu.js`로 옮길 때 메뉴/최근 메뉴 문구 복구
- `floating-portal.js`로 옮길 때 포털/챗봇/anomaly/prediction 문구 복구
- `floating-rpa-widget.js`로 옮길 때 RPA 상태/결과 문구 복구

이 방식이 좋은 이유:

- 기능별로 확인 가능
- 깨짐 복구가 어느 모듈에서 회귀났는지 추적 가능


## 7. 단계별 실행 계획

### Phase 0. 기준 동작 고정

목적:

- 분리 전에 기준 동작을 고정한다.

작업:

- 현재 전역 함수 목록 확인
- 현재 DOM id/class 계약 확인
- 현재 RPA 상태 저장 키 확인
- 현재 포털 탭 구조 확인

검증:

- `node --check frontend/services/common/sidebar.js`


### Phase 1. `sidebar-menu.js` 추출

목적:

- 좌측 메뉴와 최근 메뉴를 먼저 분리한다.

이 단계에서 할 일:

- `sidebar-menu.js` 생성
- 메뉴 렌더/토글/최근 메뉴 로직 이동
- `sidebar.js`는 해당 초기화만 호출
- 전역 브리지는 `sidebar.js`에 유지

이 단계에서 하지 않을 일:

- AI 포털 로직 변경
- RPA 위젯 변경

검증:

- 사이드바 렌더
- 메뉴 접기/펼치기
- 최근 메뉴 저장
- 모바일 메뉴 동작


### Phase 2. `floating-portal.js` 추출

목적:

- AI 포털을 메뉴 로직에서 분리한다.

이 단계에서 할 일:

- 포털 HTML/CSS 문자열 이동
- 탭 전환 로직 이동
- 챗봇 메시지 전송 로직 이동
- anomaly/prediction 렌더 로직 이동
- 드래그/리사이즈 로직 이동

이 단계에서 하지 않을 일:

- RPA 위젯 상태 저장/폴링 이동

검증:

- 포털 열기/닫기
- 챗봇 입력/응답
- anomaly 탭 렌더
- prediction 탭 렌더
- 드래그
- 리사이즈


### Phase 3. `floating-rpa-widget.js` 추출

목적:

- 가장 복잡한 상태성 로직을 마지막에 분리한다.

이 단계에서 할 일:

- RPA storage/polling/result 렌더 로직 이동
- 포털 내 RPA 탭 컨테이너와 연결
- 페이지 컨텍스트별 task type 판정 유지
- 권한별 상세 결과 제한 유지

검증:

- RPA 실행 요청
- 폴링
- 완료 후 결과 렌더
- 새로고침 후 상태 복원
- 페이지 이동 후 복원


### Phase 4. `sidebar.js` 슬림화

목적:

- 최종적으로 진입점만 남긴다.

남길 것:

- 공용 유틸
- 초기화 순서
- 전역 브리지

제거할 것:

- 메뉴 상세 구현
- 포털 상세 구현
- RPA 상세 구현

목표 크기:

- `sidebar.js` 본문 200~350줄 내외


## 8. 인코딩 복구 계획

### 8.1 복구 대상 우선순위

1. 사용자 눈에 바로 보이는 메뉴 라벨
2. 포털 탭 이름과 버튼 텍스트
3. 챗봇 기본 문구/오류 문구
4. anomaly/prediction 설명 문구
5. RPA 상태 메시지
6. 주석

### 8.2 복구 원칙

- 기능 이동과 같이 복구한다.
- 한 커밋에 너무 많은 문자열 교체를 넣지 않는다.
- 콘솔 출력이 깨져 보여도 `rg` 기준 실제 파일 내용으로 판단한다.

### 8.3 검증 방법

- `node --check`
- `rg`로 핵심 한글 키워드 검색
- 필요한 경우 실제 페이지 새로고침 확인


## 9. 유지해야 하는 계약

### 9.1 DOM 계약

유지 대상:

- `dduk-floating-chatbot-container`
- `dduk-floating-chatbot-panel`
- `dduk-floating-chatbot-trigger`
- `dduk-portal-tab`
- `dduk-portal-content-*`
- `rpaWidget*`

### 9.2 스토리지 계약

유지 대상:

- 최근 메뉴 키
- `dduk_rpa_states`

### 9.3 API 계약

유지 대상:

- `/api/v1/ai/chat`
- `/api/v1/admin/anomaly-logs`
- `/api/v1/admin/rpa/trigger`
- `/api/v1/admin/tasks/{taskId}`
- `/api/v1/inventory/dashboard/stats`
- `/api/v1/inventory/purchase-dashboard/stats`


## 10. 리스크와 대응

### 리스크 1. 전역 함수 누락

증상:

- 메뉴 클릭, 포털 열기, 버튼 동작 불능

대응:

- 전역 함수는 `sidebar.js`에 유지
- 구현만 분리 파일로 위임


### 리스크 2. 포털 탭 전환 회귀

증상:

- anomaly/prediction/rpa 탭이 비어 보임

대응:

- 포털 셸 분리 직후 탭별 렌더 확인


### 리스크 3. 리사이즈 회귀

증상:

- 오른쪽 고정이 깨짐
- 왼쪽이 아니라 오른쪽으로 커짐

대응:

- `floating-portal.js` 안에서 right edge 기준 유지 로직 보존
- 드래그/리사이즈는 분리 직후 바로 수동 확인


### 리스크 4. RPA 상태 복원 회귀

증상:

- 새로고침 후 상태 유실
- 권한 메시지 누락

대응:

- `dduk_rpa_states` 구조 변경 금지
- 상태 저장/복원 로직은 마지막 단계에서만 이동


### 리스크 5. 한글 깨짐 복구 중 문법 손상

증상:

- 템플릿 문자열 문법 오류

대응:

- 각 단계 후 `node --check`
- 긴 템플릿은 작은 블록 단위로 수정


## 11. 권장 커밋 단위

권장 커밋:

1. `docs: simplify sidebar refactor plan`
2. `refactor: extract sidebar menu module`
3. `refactor: extract floating portal module`
4. `refactor: extract floating rpa widget module`
5. `chore: slim sidebar entrypoint and recover remaining korean copy`


## 12. 검증 계획

각 단계 공통:

- `node --check` 대상 파일 전부

기능별 확인:

1. 사이드바 메뉴 접기/펼치기
2. 최근 사용 메뉴 표시
3. AI 포털 열기/닫기
4. 탭 전환
5. 챗봇 전송
6. anomaly/prediction 표시
7. RPA 실행/상태/결과
8. 드래그
9. 리사이즈

최종 확인 대상 페이지:

- `frontend/pages/inventory/purchase-dashboard.html`
- `frontend/pages/inventory/dashboard.html`
- `frontend/pages/hr/accounting/accounting_dashboard.html`
- `frontend/pages/admin/system-admin.html`


## 13. 최종 권고

이번 분리는 `4분할`이면 충분하다.

정리하면:

- `sidebar.js`는 얇게
- 메뉴는 `sidebar-menu.js`
- AI 포털은 `floating-portal.js`
- 상태성 높은 RPA는 `floating-rpa-widget.js`

이 구조가 지금 프로젝트에선 가장 현실적인 타협안이다.

