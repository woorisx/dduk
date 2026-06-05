# GitHub Actions Deploy Setup

> [!IMPORTANT]
> **📢 개인 저장소(Fork) 배포 실습 시 필수 직접 수정 사항**
> 팀원이 개인 저장소로 Fork하여 배포 실습을 진행할 때, **다음 3가지 항목은 본인의 환경에 맞추어 반드시 직접 수정**해야 정상적으로 배포에 성공합니다:
> 
> 1. **Git Clone 주소 변경 (4번 항목 참고)**
>    - `git clone https://github.com/<본인-깃허브-계정>/dduk.git` ➡️ 본인의 깃허브 계정명 주소로 변경하여 EC2에 클론해야 합니다.
> 2. **GitHub Secrets 7종 직접 등록 (2번 항목 참고)**
>    - 본인 EC2 인스턴스의 IP(`EC2_HOST`), 본인 SSH 개인키 pem(`EC2_SSH_KEY`), 본인의 사용자명(`GHCR_USERNAME`) 등을 본인의 Fork된 저장소 Secrets에 직접 등록해야 합니다. (Secrets 복사 시 공백/개행 주의!)
> 3. **워크플로우 트리거 브랜치명 수정 (7번 항목 참고)**
>    - `.github/workflows/deploy-kmh.yml` 파일 내부의 `on.push.branches` 부분을 본인의 작업/실습 브랜치명(예: `wooree`)으로 반드시 직접 수정하고 push해야 GitHub Actions 배포가 작동합니다.

이 문서는 `develop` 브랜치 push 시 GitHub Actions가 Docker 이미지를 빌드해서 GHCR에 올리고, EC2는 그 이미지를 `pull`만 해서 배포하는 흐름을 설명한다.

대상 브랜치:

- `develop`

배포 구조:

1. GitHub Actions가 `backend`, `ai-server`, `rpa-server` 이미지를 빌드한다.
2. 이미지를 GHCR(`ghcr.io`)에 push한다.
3. Actions가 EC2에 SSH 접속한다.
4. EC2는 GHCR 로그인 후 `docker compose -f docker-compose.deploy.yml pull`을 수행한다.
5. EC2는 `docker compose -f docker-compose.deploy.yml up -d --force-recreate`로 컨테이너를 재기동한다.

이 구조는 작은 EC2 인스턴스에서 `Gradle bootJar`를 직접 빌드하지 않아도 되게 만드는 게 핵심이다.

## 1. 워크플로우 파일

저장소에는 아래 파일이 있어야 한다.

- [deploy-kmh.yml](/C:/kmh/dduk/.github/workflows/deploy-kmh.yml:1)

이 워크플로우는 `develop` 브랜치 push 또는 수동 실행 시 동작한다.

## 2. GitHub Secrets

경로:

`Settings -> Secrets and variables -> Actions -> New repository secret`

필수 Secrets는 아래 7개다.

### `EC2_HOST`

EC2 퍼블릭 IP 또는 도메인

예시:

```text
3.36.71.14
```

### `EC2_USER`

EC2 SSH 접속 계정

예시:

```text
ubuntu
```

### `EC2_PORT`

SSH 포트

예시:

```text
22
```

비워도 워크플로우는 기본값 `22`를 사용한다.

### `EC2_DEPLOY_PATH`

EC2 안에서 저장소가 있는 경로

예시:

```text
/home/ubuntu/dduk
```

### `EC2_SSH_KEY`

`pem` 파일 내용 전체

현재 기준 파일:

```text
C:\kmh\aws dduk\dduk-erp-key.pem
```

주의:

- 파일 경로를 넣는 게 아니라 파일 내용 전체를 넣어야 한다.
- `-----BEGIN ...` 부터 `-----END ...` 까지 모두 복사해야 한다.
- pem 파일 자체는 저장소에 commit하면 안 된다.

### `GHCR_USERNAME`

GHCR 로그인에 사용할 GitHub 사용자명

예시:

```text
<본인-깃허브-계정>
```

### `GHCR_READ_TOKEN`

EC2가 GHCR에서 이미지를 pull할 때 쓸 GitHub Personal Access Token

권장 권한:

- `read:packages`

주의:

- 이 토큰은 EC2 `docker login ghcr.io` 용도다.
- GitHub Actions가 GHCR에 push할 때는 별도 secret 없이 내장 `GITHUB_TOKEN`을 쓴다.

## 3. GHCR 토큰 만드는 방법

1. GitHub 우측 상단 프로필
2. `Settings`
3. `Developer settings`
4. `Personal access tokens`
5. `Tokens (classic)` 또는 fine-grained token 생성

권장:

- classic token이면 `read:packages`
- package가 private이고 pull만 필요하면 `read:packages`만 먼저 시도

만약 EC2 pull에서 권한 오류가 나면:

- package visibility
- token 소유 계정
- repository/package 연결 상태

이 3개를 먼저 확인한다.

## 4. EC2 쪽 준비물

EC2에는 아래가 준비되어 있어야 한다.

- Docker Engine
- Docker Compose plugin
- 저장소 clone 완료
- 배포 경로에 `.env.aws` 존재

예시:

```bash
cd /home/ubuntu
git clone https://github.com/<본인-깃허브-계정>/dduk.git dduk
cd dduk
git checkout develop
cp .env.aws.example .env.aws
```

그 다음 `.env.aws`에 실제 운영값을 채운다.

## 5. EC2에서 실제로 실행되는 배포 명령

워크플로우는 EC2에서 아래 흐름을 수행한다.

```bash
cd /home/ubuntu/dduk
git fetch origin
git checkout develop
git pull --ff-only origin develop
docker login ghcr.io
docker compose --env-file .env.aws -f docker-compose.deploy.yml config
docker compose --env-file .env.aws -f docker-compose.deploy.yml pull
docker compose --env-file .env.aws -f docker-compose.deploy.yml up -d --force-recreate
docker compose --env-file .env.aws -f docker-compose.deploy.yml ps
```

그 뒤 health check:

```bash
curl -fsS http://localhost:8080/index.html >/dev/null
curl -fsS http://localhost:5000/health
curl -fsS http://localhost:5050/health
```

## 6. 이미지 이름 규칙

워크플로우는 기본적으로 아래 태그를 push한다.

- `ghcr.io/<repo-owner>/dduk-backend:develop`
- `ghcr.io/<repo-owner>/dduk-ai-server:develop`
- `ghcr.io/<repo-owner>/dduk-rpa-server:develop`

추적용 SHA 태그도 같이 push한다.

- `ghcr.io/<repo-owner>/dduk-backend:sha-<commit-sha>`
- `ghcr.io/<repo-owner>/dduk-ai-server:sha-<commit-sha>`
- `ghcr.io/<repo-owner>/dduk-rpa-server:sha-<commit-sha>`

EC2 배포는 기본적으로 `:develop` 태그를 당겨 쓴다.

## 7. 수동 테스트 방법

### 💡 개인 레포지토리 맞춤 브랜치 설정 가이드
개인 저장소에서 본인만의 작업 브랜치(예: `wooree` 등)를 사용해 배포 실습을 하려는 경우, GitHub Actions가 이를 트리거할 수 있도록 아래 설정을 수정해야 합니다.

1. **[.github/workflows/deploy-kmh.yml](file:///C:/kmh/dduk/.github/workflows/deploy-kmh.yml)** 파일을 엽니다.
2. `on.push.branches` 항목에 본인의 브랜치명을 추가해 줍니다:
   ```yaml
   on:
     push:
       branches:
         - develop
         - <본인-작업-브랜치-이름> (예: wooree)
   ```
3. 수정 후 본인의 브랜치로 push하면 워크플로우가 감지되어 빌드/배포를 시작하며, 빌드되는 이미지 태그도 본인의 브랜치명(예: `:wooree`)으로 자동 업로드됩니다.

### 방법 1. 배포 대상 브랜치 push

```bash
git push origin <본인-작업-브랜치>
```

### 방법 2. GitHub Actions 수동 실행

1. `Actions`
2. `Deploy develop to EC2` (또는 설정된 워크플로우 이름)
3. `Run workflow`

## 8. 실패하면 먼저 볼 것

### GHCR 로그인 실패

- `GHCR_USERNAME`
- `GHCR_READ_TOKEN`
- GHCR package visibility

### SSH 접속 실패

- `EC2_HOST`
- `EC2_USER`
- `EC2_PORT`
- `EC2_SSH_KEY`

### 서버 경로 오류

- `EC2_DEPLOY_PATH`

### 컨테이너 기동 실패

```bash
docker compose --env-file .env.aws -f docker-compose.deploy.yml logs --tail=100 backend
docker compose --env-file .env.aws -f docker-compose.deploy.yml logs --tail=100 ai-server
docker compose --env-file .env.aws -f docker-compose.deploy.yml logs --tail=100 rpa-server
```

## 9. 보안 주의

- pem 파일은 GitHub Secrets에만 넣고 저장소에는 올리지 않는다.
- DB 비밀번호, JWT secret, Gemini key, RPA callback token은 계속 `.env.aws`에서만 관리한다.
- `GHCR_READ_TOKEN`은 pull 용도만 주고, 불필요하게 넓은 GitHub 권한은 주지 않는 게 좋다.
