# DDUK ERP Project

인사, 재고, 회계, 관리자 기능과 AI/OCR/RPA 보조 기능을 한 저장소에서 함께 다루는 ERP 포트폴리오 프로젝트다.

## 프로젝트 개요

- 백엔드: Spring Boot 기반 ERP API
- 프론트엔드: HTML/CSS/Vanilla JS 기반 멀티 페이지 UI
- AI 서버: Flask 기반 챗봇/OCR 보조 API
- RPA 서버: Flask + Playwright 기반 비동기 업무 자동화 트리거 서버
- 데이터: 부트스트랩 데이터 + 데모 샘플 데이터로 로컬 시연 가능

현재 저장소는 다음 흐름을 기준으로 구성돼 있다.

`frontend/pages -> frontend/services|styles|assets -> /api/v1/... -> backend`

## 주요 기능

### 공통/관리자

- JWT 기반 로그인 및 권한 분기
- 관리자 대시보드
- 공지사항, 멤버 관리, 작업 이력, 이상 탐지 화면
- 공통 사이드바, AI 포털, RPA 위젯

### 재고 / 구매

- 재고 대시보드
- 발주 요청, 발주 목록, 발주 상태 관리
- 입고 처리, 창고 이동, 재주문, 거래처 관리
- RPA 기반 발주 단가 비교 / 재고 부족 분석 연계

### 인사 / 회계

- 직원/근태/급여 기준 정보 화면
- 회계 대시보드
- 전표 관리, 시산표, 리포트, 월 마감
- 비용 처리, 세금계산서 화면

### OCR / AI / RPA

- OCR 증빙 업로드 및 문서함
- Gemini 기반 챗봇 API
- Gemini 기반 OCR 파싱 API
- Playwright 기반 외부 데이터 수집/검증 자동화

## 기술 스택

| 영역 | 현재 스택 |
| :--- | :--- |
| Frontend | HTML5, CSS3, Vanilla JavaScript |
| Backend | Java 21, Spring Boot 3.3, Spring Web, Spring Data JPA, Spring Security, Spring Validation |
| Auth | JWT (`jjwt`) |
| Database | MySQL Driver 기반 구성, MySQL 호환 DB 스키마/시드 사용 |
| AI Server | Python, Flask, Flask-CORS, Waitress, `google-generativeai`, `python-dotenv` |
| OCR | Gemini 기반 OCR 파싱 경로 |
| RPA | Python, Flask, Requests, Playwright |
| Build / Tooling | Gradle Wrapper, Lombok |
| Local Config | `.env` 기반 환경 변수 주입 |

### 스택 메모

- 현재 `backend/build.gradle` 기준 QueryDSL 의존성은 없다.
- 현재 `ai-server/requirements.txt` 기준 OpenAI SDK, Pandas 의존성은 없다.
- 현재 프론트엔드는 `frontend/package.json` 없이 정적 HTML 페이지 방식으로 동작한다.
- `.env.example`에는 TiDB Cloud 예시가 들어 있지만, 애플리케이션 설정은 MySQL 드라이버와 MySQL 호환 SQL 초기화 기준으로 작성돼 있다.

## 디렉터리 구조

```text
backend/      Spring Boot ERP API
frontend/     HTML/CSS/Vanilla JS UI
ai-server/    Flask 기반 AI API
rpa/          Flask + Playwright 기반 RPA 트리거 서버
docs/         프로젝트 문서, 규칙, 가이드
```

## 실행 전 준비

필수 런타임:

- Java 21
- Python 3.10+
- MySQL 호환 DB

권장:

- Git
- 정적 파일을 띄울 수 있는 로컬 서버

환경 변수는 루트의 `.env.example`를 복사해서 `.env`로 맞추면 된다.

## 로컬 실행

### 1. 저장소 준비

```bash
git clone <repository-url>
cd dduk
```

### 2. 백엔드 실행

```bash
cd backend
./gradlew bootRun
```

기본 백엔드 주소:

- `http://localhost:8080`

### 3. AI 서버 실행

```bash
cd ai-server
python app.py
```

기본 AI 서버 주소:

- `http://localhost:5000`
- 헬스 체크: `http://localhost:5000/health`

### 4. RPA 서버 실행

```bash
cd rpa
python app.py
```

기본 RPA 서버 주소:

- `http://localhost:5050`
- 헬스 체크: `http://localhost:5050/health`

### 5. 프론트엔드 확인

`frontend/`는 번들 빌드가 아니라 정적 페이지 구조라서, Live Server 같은 정적 서버로 열어서 확인하면 된다.

주요 페이지는 `frontend/pages/` 아래에 도메인별로 정리돼 있다.

- `frontend/pages/admin/`
- `frontend/pages/hr/`
- `frontend/pages/inventory/`
- `frontend/pages/ocr/`

## 데이터 초기화

현재 백엔드는 `application.yml` 기준으로 아래 SQL을 초기화에 사용한다.

- 스키마:
  - `db/mysql/dduk_bootstrap_schema.sql`
  - `db/mysql/task_history_schema.sql`
  - `db/mysql/anomaly_log_schema.sql`
  - `db/mysql/notice_schema.sql`
  - `db/mysql/ocr_schema.sql`
  - `db/mysql/tax_invoice_schema.sql`
- 데이터:
  - `db/mysql/dduk_bootstrap_data.sql`
  - `db/mysql/dduk_sample_data.sql`

즉 로컬 기동 시 최소 부트스트랩 데이터와 데모 샘플 데이터가 함께 올라가는 구성이 기본값이다.

## 참고 문서

- [AI Harness](./docs/AI_HARNESS.md)
- [Convention](./docs/CONVENTION.md)
- [System Architecture](./docs/common/SYSTEM_ARCHITECTURE.md)
- [API Standard](./docs/common/API_STANDARD.md)

## 현재 README 정리 기준

이 README는 실제 저장소 기준으로 아래 파일을 확인해서 맞췄다.

- `backend/build.gradle`
- `backend/src/main/resources/application.yml`
- `ai-server/requirements.txt`
- `ai-server/app.py`
- `rpa/requirements.txt`
- `rpa/app.py`
- `.env.example`

기술 스택이나 실행 방법이 바뀌면 위 파일과 같이 업데이트하는 걸 권장한다.
