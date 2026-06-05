# 1단계: 빌드 스테이지
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# 프로젝트 루트 컨텍스트에서 backend 및 frontend 복사
COPY backend backend
COPY frontend frontend

# frontend 정적 파일을 Spring Boot의 static 디렉토리로 복사하여 팩킹 준비
RUN mkdir -p backend/src/main/resources/static && cp -r frontend/* backend/src/main/resources/static/

WORKDIR /app/backend
RUN chmod +x gradlew
RUN ./gradlew bootJar --no-daemon

# 2단계: 실행 스테이지
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/backend/build/libs/dduk-backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "app.jar"]
