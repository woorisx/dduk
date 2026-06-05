# DDUK ERP 구조 초안

이 문서는 팀원이 실제로 어디에 무엇을 만들지 빠르게 판단할 수 있게 도메인 책임과 경로 예시를 정리한 문서다.

---

## 1. 전체 구조

```text
backend/
frontend/
ai-server/
rpa/
docs/
```

각 폴더는 기술 스택 기준으로 나뉘고, 그 안에서 다시 도메인 기준으로 분리한다.

---

## 2. 도메인 분리 원칙

| 도메인 | 책임 |
| :--- | :--- |
| `admin` | 로그인, 권한, 공통 설정, 관리자 대시보드 |
| `hr` | 직원, 근태, 급여, 비용 처리 |
| `inventory` | 거래처, 재고, 발주, 입출고 |

원칙:

- API, 서비스, 화면은 가능하면 같은 도메인 단위로 묶는다.
- 공통 로직은 `common` 또는 `shared` 성격으로 분리한다.
- 도메인 간 직접 참조를 늘리기보다 공용 계약 DTO, 서비스 인터페이스, API 호출을 통해 연결한다.

---

## 3. 백엔드 구조 예시

예시 패키지 구조:

```text
backend/src/main/java/.../
  admin/
    controller/
    service/
    dto/
  hr/
    controller/
    service/
    entity/
    repository/
    dto/
  inventory/
    controller/
    service/
    entity/
    repository/
    dto/
  common/
    config/
    security/
    exception/
    response/
```

권장 역할:

- `controller`: 요청/응답 처리
- `service`: 업무 규칙
- `repository`: DB 접근
- `dto`: API 입출력 모델
- `entity`: JPA 엔티티
- `common`: 인증, 공통 응답, 예외, 설정

---

## 4. 프론트 구조 예시

현재 폴더 기준:

```text
frontend/
  assets/
  components/
  pages/
  services/
  styles/
  index.html
```

권장 세부 분리:

```text
frontend/pages/
  admin/
  hr/
  inventory/

frontend/components/
  common/
  admin/
  hr/
  inventory/

frontend/services/
  api-client.js
  auth-service.js
  hr-service.js
  inventory-service.js
```

원칙:

- 화면은 `pages/`, 재사용 UI는 `components/`, API 호출은 `services/`에 둔다.
- 공통 레이아웃, 모달, 테이블 같은 건 도메인 전용으로 복붙하지 말고 공통 컴포넌트로 올린다.

---

## 5. AI 서버 구조 예시

현재 폴더 기준:

```text
ai-server/
  api/
  models/
  services/
  utils/
```

권장 역할:

- `api/`: Flask route, request parsing
- `services/`: 챗봇, OCR, 분석 로직
- `models/`: 입출력 스키마 또는 데이터 모델
- `utils/`: 로깅, 파일 처리, 공통 유틸

도메인 연결 예시:

- `services/chatbot_service.py`
- `services/ocr_service.py`
- `services/expense_analysis_service.py`

---

## 6. RPA 구조 예시

현재 폴더 기준:

```text
rpa/
  engine/
  outputs/
  tasks/
```

권장 역할:

- `engine/`: 브라우저 실행기, 로그인 세션, 공통 유틸
- `tasks/`: 거래처별/업무별 자동화 시나리오
- `outputs/`: 다운로드 파일, 캡처, 결과 로그

예시:

```text
rpa/tasks/
  supplier_login_task.py
  purchase_order_download_task.py
  receipt_upload_task.py
```

---

## 7. 우선 구현 순서

1. `backend/common`에 인증, 공통 응답, 예외 구조 확정
2. `admin` 로그인/권한 API 작성
3. `hr`, `inventory` 핵심 CRUD 작성
4. `frontend` 도메인별 목록/등록 화면 작성
5. `ai-server`, `rpa`는 독립 진입점으로 순차 연결

---

## 8. 문서 운영 규칙

- 구조가 바뀌면 코드보다 먼저 이 문서를 갱신한다.
- 팀원이 새 폴더를 만들 때는 도메인 책임과 공통 여부를 같이 적는다.
- 구조 충돌이 생기면 [docs/CONVENTION.md](/C:/kmh/dduk/docs/CONVENTION.md:1)을 우선 기준으로 본다.
