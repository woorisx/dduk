# DDUK ERP 환경변수 가이드

이 문서는 현재 프로젝트의 설정 구조와 환경변수 사용 방식을 설명한다.

## 1. 목적

현재 프로젝트는 백엔드, AI 서버, RPA가 각각 다른 실행 환경을 가질 수 있으므로 실제 값은 환경변수로 관리하고, 저장소에는 샘플만 남기는 방식을 사용한다.

통합 샘플 파일:

- [`.env.example`](/C:/kmh/dduk/.env.example:1)

## 2. 현재 설정 구조

### 2-1. `application.yml`

[application.yml](/C:/kmh/dduk/backend/src/main/resources/application.yml:1) 의 역할:

- 설정 키 구조 정의
- 일부 안전한 기본값 정의
- 실제 비밀값은 직접 보관하지 않음

현재 필수 환경값:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_SECRET`

현재 기본값이 남아 있는 항목:

- `JPA_DDL_AUTO` 기본값 `update`
- `JWT_EXPIRATION` 기본값 `86400000`

### 2-2. 루트 `.env`

로컬 개발에서는 프로젝트 루트 `.env` 를 사용한다.

- 실제 DB 주소
- 실제 DB 계정
- JWT 시크릿
- 로컬 실행용 기타 값

현재 백엔드 시작 시 [DdukApplication.java](/C:/kmh/dduk/backend/src/main/java/com/dduk/DdukApplication.java:1) 에서 루트 `.env` 를 읽어 system property 로 주입한다.

### 2-3. 운영 환경

운영/배포 환경에서는 `.env` 대신 실제 환경변수 주입을 권장한다.

## 3. 사용 원칙

1. 저장소에는 `.env.example` 만 둔다.
2. 실제 비밀값은 `.env` 또는 환경변수로만 관리한다.
3. `application.yml` 에 로컬 비밀값이나 운영 비밀값을 직접 넣지 않는다.
4. 같은 값을 `application.yml` 과 `.env` 양쪽에 중복 정의하지 않는다.

## 4. Backend 변수 설명

### 4-1. 필수

- `DB_URL`: DB 접속 문자열
- `DB_USERNAME`: DB 계정
- `DB_PASSWORD`: DB 비밀번호
- `JWT_SECRET`: JWT 서명용 secret

### 4-2. 선택

- `JPA_DDL_AUTO`: 보통 로컬은 `update`, 검증 환경은 `validate`
- `JWT_EXPIRATION`: 토큰 만료 시간
- `DB_DIALECT`: 참고용 메모 변수
- `DB_POOL_SIZE`: 참고용 메모 변수

### 4-3. TiDB 예시

```env
DB_URL=jdbc:mysql://{TIDB_HOST}:4000/dduk_erp?useSSL=true&serverTimezone=Asia/Seoul
DB_USERNAME={TIDB_USER}
DB_PASSWORD={TIDB_PASSWORD}
JPA_DDL_AUTO=update
JWT_SECRET=replace_with_a_long_random_secret_at_least_64_chars
JWT_EXPIRATION=86400000
```

### 4-4. 로컬 MySQL 예시

```env
DB_URL=jdbc:mysql://localhost:3306/dduk_erp?useSSL=false&serverTimezone=Asia/Seoul&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true
DB_USERNAME=root
DB_PASSWORD=change_me
JPA_DDL_AUTO=update
JWT_SECRET=replace_with_a_long_random_secret_at_least_64_chars
JWT_EXPIRATION=86400000
```

## 5. AI Server 변수 설명

주요 값:

- `FLASK_ENV`
- `FLASK_DEBUG`
- `AI_SERVER_HOST`
- `AI_SERVER_PORT`
- `OPENAI_API_KEY`
- `OPENAI_MODEL`
- `OCR_PROVIDER`
- `BACKEND_API_BASE_URL`

예시:

```env
FLASK_ENV=development
FLASK_DEBUG=true
AI_SERVER_HOST=127.0.0.1
AI_SERVER_PORT=5000
OPENAI_API_KEY=replace_with_api_key
OPENAI_MODEL=gpt-4o-mini
OCR_PROVIDER=tesseract
BACKEND_API_BASE_URL=http://localhost:8080/api/v1
```

## 6. RPA 변수 설명

주요 값:

- `RPA_BROWSER`
- `RPA_HEADLESS`
- `RPA_BASE_URL`
- `RPA_LOGIN_ID`
- `RPA_LOGIN_PASSWORD`
- `PLAYWRIGHT_TIMEOUT_MS`

예시:

```env
RPA_BROWSER=chromium
RPA_HEADLESS=true
RPA_BASE_URL=http://localhost:5500
RPA_LOGIN_ID=admin
RPA_LOGIN_PASSWORD=admin123
PLAYWRIGHT_TIMEOUT_MS=30000
```

## 7. 권장 운영 방식

1. 저장소에는 루트 `.env.example` 만 포함
2. 팀원은 `.env.example` 를 참고해 로컬 `.env` 생성
3. 로컬 실행은 `.env` 또는 셸 환경변수 사용
4. 운영 실행은 배포 도구에서 환경변수 주입
5. 비밀값 변경 시 샘플 파일에는 키 이름과 예시만 유지

## 8. 주의사항

- 테스트 계정과 비밀번호는 로컬 개발 기준이다.
- 운영 비밀번호, API key, DB 계정은 저장소에 넣지 않는다.
- `application.yml` 은 실제 값 저장소가 아니라 설정 구조 파일이다.
- 필수 값 누락 시 백엔드가 시작되지 않을 수 있다.

## 9. 빠른 점검 체크리스트

- 루트 `.env` 가 있는지
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 가 실제 값인지
- `JWT_SECRET` 이 실제 긴 문자열인지
- `application.yml` 에 비밀값이 하드코딩되지 않았는지
- `.env.example` 와 실제 사용 키가 맞는지
