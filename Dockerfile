# =============================================================================
# Multi-stage Dockerfile — spring-bootstrap
#
# Stage 1 (builder) : Gradle 빌드 및 JAR 생성
# Stage 2 (runtime) : 최소 JRE 이미지로 실행
# =============================================================================

# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

# Gradle Wrapper와 빌드 설정 파일만 먼저 복사 (의존성 캐시 레이어 분리)
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY gradle.properties .

# 의존성 다운로드 (소스 변경 시 재사용)
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q 2>/dev/null || true

# 소스 코드 복사 후 빌드 (테스트 제외 — 컨테이너 빌드 시)
COPY src src
COPY config config

RUN ./gradlew bootJar -x test -x checkstyleMain -x checkstyleTest -x spotbugsMain -x spotbugsTest --no-daemon -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# 보안: non-root 사용자로 실행
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# 로그 디렉토리 생성
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app

# JAR 복사
COPY --from=builder /workspace/build/libs/*.jar app.jar

USER appuser

# Spring Boot Actuator 헬스체크
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", "app.jar"]
