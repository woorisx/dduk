# DDUK ERP Project Agents

이 프로젝트에서 작업하는 AI는 먼저 [docs/AI_HARNESS.md](/C:/kmh/dduk/docs/AI_HARNESS.md:1)를 기준으로 따른다.

핵심 적용 원칙:

- 사용자에게 보내는 최종 응답 첫 줄은 가능하면 정확히 `뚝 erp 하네스 적용 중!`으로 시작한다.
- 프로젝트 구조는 `backend/`, `frontend/`, `ai-server/`, `rpa/`, `docs/`를 유지한다.
- 화면 HTML 파일은 기본적으로 `frontend/pages/[도메인]/` 아래에 두고, `backend/src/main/resources/static`를 새 UI 페이지의 기본 위치로 사용하지 않는다.
- 프론트 작업 흐름은 `frontend/pages -> frontend/services|styles|assets -> /api/v1/... -> backend` 순서를 기준으로 유지한다.
- 작업 전 요청을 `hr`, `inventory`, `admin` 중 어느 도메인인지 먼저 판단한다.
- 백엔드 코드는 반드시 계층형 구조(`com.dduk.controller`, `com.dduk.service`, `com.dduk.repository`, `com.dduk.entity`, `com.dduk.dto`, `com.dduk.config`)를 준수하며, `com.dduk.domain` 패키지는 일체 사용하지 않는다. 각 도메인(accounting, hr, inventory, admin)은 계층 폴더 하위의 서브 패키지로 구성한다.
- 백엔드 계층형 구조 규칙은 역할 분리를 뜻하며, 화면 파일 위치를 백엔드 폴더로 옮기는 근거로 쓰지 않는다.
- 읽기 쉬운 코드, 유지보수 가능한 구조, 과도한 추상화 지양을 우선한다.
- 존재하지 않는 테이블, 컬럼, API를 구현 사실처럼 단정하지 않는다.
- 보안 기본 인증, 권한, 입력 검증, 민감정보 비노출은 항상 고려한다.
- 현재 프로젝트에 멀티테넌트가 실제 도입되지 않았다면 `tenant_id`를 임의로 코드에 추가하지 않는다.

적용 우선순위:

1. 실제 코드와 확정 문서
2. [docs/AI_HARNESS.md](/C:/kmh/dduk/docs/AI_HARNESS.md:1)
3. [docs/CONVENTION.md](/C:/kmh/dduk/docs/CONVENTION.md:1)
4. 그 외 초안 문서

문서가 비어 있는 경우:

- 초안 제안은 가능하다.
- 다만 초안, 가정, 추정 중 하나로 명시한다.
- 영향 범위를 짧게 설명한다.
