# DDUK ERP 실행 및 테스트 가이드

이 문서는 현재 프로젝트를 로컬에서 실행하고 기본 동작을 확인하는 절차를 정리한 가이드다.

## 1. 준비 환경

- Java 21
- MySQL 8.0 또는 TiDB 호환 DB
- Python 3.10 이상
- Git

프론트엔드는 별도 빌드 없이 정적 HTML/CSS/JS로 동작한다.

## 2. 현재 확인 가능한 범위

- Spring Boot 백엔드 실행
- DB 스키마/기초 데이터 초기화
- 로그인 API 확인
- 프론트엔드 로그인 및 기본 화면 이동 확인
- 관리자 계정 관리 기능 확인

## 3. 프로젝트 경로

- backend: `C:\kmh\dduk\backend`
- frontend: `C:\kmh\dduk\frontend`
- 환경변수 샘플: `C:\kmh\dduk\.env.example`

## 4. 설정 구조

현재 설정 책임은 아래처럼 나뉜다.

- `backend/src/main/resources/application.yml`
  - 설정 키 구조와 일부 안전한 기본값만 관리
- 루트 `.env`
  - 로컬 개발용 실제 값 관리
- OS 환경변수
  - 운영 또는 CI 실행 시 실제 값 주입

중요:

- `application.yml` 에는 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` fallback 이 없다.
- 따라서 로컬 실행 전 `.env` 또는 환경변수 준비가 반드시 필요하다.
- `.env` 샘플은 루트 [`.env.example`](/C:/kmh/dduk/.env.example:1) 를 기준으로 맞춘다.

## 5. DB 준비

### 5-1. MySQL 접속 예시

```bash
mysql -u root -p
```

### 5-2. 수동 schema 실행 여부

현재 백엔드는 시작 시 아래 작업을 자동으로 시도한다.

- `dduk_erp` 사용
- bootstrap schema 실행
- bootstrap data 실행
- JPA 스키마 보정

즉 DB 서버가 정상 접근 가능하면 schema/data SQL을 따로 먼저 실행할 필요는 없다.

## 6. 로컬 환경변수 준비

### 6-1. 권장 방법

프로젝트 루트에 `.env` 파일을 두고 실행한다.

```env
DB_URL=jdbc:mysql://localhost:3306/dduk_erp?useSSL=false&serverTimezone=Asia/Seoul&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true
DB_USERNAME=root
DB_PASSWORD=change_me
JPA_DDL_AUTO=update
JWT_SECRET=replace_with_a_long_random_secret_at_least_64_chars
JWT_EXPIRATION=86400000
```

### 6-2. PowerShell에서 직접 주입하는 방법

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/dduk_erp?useSSL=false&serverTimezone=Asia/Seoul&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="여기에_DB_비밀번호"
$env:JPA_DDL_AUTO="update"
$env:JWT_SECRET="여기에_충분히_긴_JWT_시크릿_문자열"
$env:JWT_EXPIRATION="86400000"
```

주의:

- `JWT_SECRET` 은 `.env.example` 의 예시 문구를 그대로 쓰지 말고 실제 긴 문자열로 바꿔야 한다.
- 운영 환경에서는 `.env` 대신 실제 환경변수 주입을 권장한다.

## 7. 백엔드 실행

```bash
cd C:\kmh\dduk\backend
.\gradlew.bat bootRun
```

정상 실행되면 기본 주소는 `http://localhost:8080` 이다.

시작 중 자동 처리되는 항목:

1. DB 연결
2. bootstrap schema 실행
3. bootstrap data 실행
4. JPA 엔티티 기준 보정

## 8. 로그인 API 빠른 확인

```bash
curl -X POST http://localhost:8080/api/v1/auth/login ^
  -H "Content-Type: application/json" ^
  -d "{\"loginId\":\"admin\",\"password\":\"admin123\"}"
```

예상 응답 예시:

```json
{
  "token": "...",
  "loginId": "admin",
  "name": "System Admin",
  "role": "ADMIN"
}
```

## 9. 프론트엔드 실행

### 9-1. 가장 간단한 방법

아래 파일을 직접 열어도 된다.

- `C:\kmh\dduk\frontend\index.html`

### 9-2. 권장 방법

```bash
cd C:\kmh\dduk\frontend
python -m http.server 5500
```

브라우저 접속:

- `http://localhost:5500`

현재 프론트엔드는 `localhost` 에서 열리면 백엔드 API를 `http://localhost:8080` 으로 호출한다.

## 10. 초기 로그인 계정

| 용도 | loginId | password | role |
| :--- | :--- | :--- | :--- |
| 관리자 | `admin` | `admin123` | `ADMIN` |
| 구매/발주 담당 | `inventory` | `inv123` | `INVENTORY` |
| 인사/회계 담당 | `hr` | `hr123` | `HR` |

## 11. 기본 테스트 시나리오

### 11-1. 관리자 로그인 확인

1. `frontend/index.html` 또는 `http://localhost:5500` 접속
2. `admin` / `admin123` 입력
3. 로그인 성공 후 관리자 화면 이동 확인

### 11-2. 관리자 계정 관리 확인

1. 계정 목록 조회
2. 새 계정 생성
3. 기존 계정 역할 변경
4. 계정 활성/비활성 변경

### 11-3. 역할별 접근 확인

#### 구매/발주 계정

1. `inventory` / `inv123` 로그인
2. 재고/발주 메인 화면 이동 확인
3. 관리자 페이지 직접 접근 차단 확인

#### 인사/회계 계정

1. `hr` / `hr123` 로그인
2. 인사/회계 메인 화면 이동 확인
3. 관리자 페이지 직접 접근 차단 확인

## 12. 자주 막히는 문제

### 12-1. 백엔드가 안 뜸

확인할 것:

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 가 실제 값인지
- 루트 `.env` 가 존재하는지
- DB 서버가 실제로 떠 있는지
- `JWT_SECRET` 이 비어 있지 않은지

### 12-2. DB 연결 실패

확인할 것:

- `DB_URL` 호스트/포트가 맞는지
- 로컬 MySQL이면 `localhost:3306` 접근이 되는지
- TiDB면 `4000` 포트와 계정 정보가 맞는지
- 방화벽 또는 외부 접속 제한이 없는지

### 12-3. JWT 오류

`application.yml` 은 `JWT_SECRET` 환경변수만 읽는다.

- 값이 없으면 로그인/토큰 생성이 깨질 수 있다.
- 샘플 문구 그대로 쓰지 말고 실제 긴 문자열로 바꿔야 한다.

### 12-4. 프론트 API 호출 실패

확인할 것:

- 백엔드가 `http://localhost:8080` 에서 실행 중인지
- 프론트가 `localhost` 기준으로 열려 있는지
- 브라우저 콘솔에 CORS 오류가 없는지

### 12-5. Gradle 명령이 안 됨

이 저장소는 Gradle Wrapper 를 포함한다.

- Windows: `.\gradlew.bat bootRun`
- macOS/Linux: `./gradlew bootRun`

## 13. 체크리스트

- DB 서버 실행
- 루트 `.env` 준비 또는 환경변수 주입 완료
- backend 실행 성공
- frontend 접속 성공
- 관리자 로그인 성공
- 역할별 계정 로그인 성공

## 14. UI 미리보기 모드

백엔드를 띄우지 않고 화면만 빠르게 보려면 `frontend/services/common/session.js` 에서 preview 계정 반환 코드를 사용할 수 있다.

주의:

- 이 모드는 UI 확인용이다.
- 실제 API 연동 테스트 전에는 반드시 원복해야 한다.

## 15. 다음 권장 작업

1. 관리자 계정 생성/수정 기능 추가 확인
2. `HR`, `INVENTORY` 도메인 CRUD API 연동 확인
3. 각 메인 페이지 목록 데이터 연결 상태 점검
