# syntax=docker/dockerfile:1
#
# post-flow API image. 독립 Gradle 프로젝트(rootProject=postflow-api) — 빌드 컨텍스트 = apps/api.
#   docker build -f apps/api/Dockerfile -t synub-postflow-api apps/api
# (CI: context=apps/api, file=apps/api/Dockerfile.)
#
# 런타임 env 계약(서버 compose가 주입): SPRING_PROFILES_ACTIVE=prod,
#   SPRING_DATASOURCE_USERNAME/PASSWORD(필수), SPRING_DATASOURCE_URL(선택 — 기본 db:5432/postflow),
#   ANTHROPIC_API_KEY, THREADS_APP_ID/SECRET, SERVICE_API_KEY, BILLING_WEBHOOK_SECRET, BILLING_BASE_URL,
#   SSO_ISSUER/SSO_JWKS_URI, APP_CORS_ALLOWED_ORIGINS, AWS_S3_*, AUTH_STATE_SECRET. 시크릿은 이미지에 안 넣는다.

# --- build stage: Gradle bootJar (JDK 21) ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 래퍼/설정 + 빌드파일 먼저 → 의존성 레이어 캐싱.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# 소스 후 boot jar 빌드(테스트 제외).
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# --- run stage: JRE 21 runtime ---
FROM eclipse-temurin:21-jre
# 컨테이너 타임존 = 한국(KST) — 로그 타임스탬프 KST. 저장·Instant 직렬화는 UTC 유지.
ENV TZ=Asia/Seoul
WORKDIR /app
RUN groupadd -g 10001 app && useradd -u 10001 -g app -r app
COPY --from=build /app/build/libs/*.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
