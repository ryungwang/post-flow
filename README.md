# PostFlow API

Create Once. Grow Automatically. — AI 콘텐츠 자동 생성·예약·발행 플랫폼의 백엔드.

자세한 제품 사양은 [`prd.md`](./prd.md) 참조.

## Tech Stack

- Java 21 · Spring Boot 3.4 · Spring Security · JPA · QueryDSL · Redis
- PostgreSQL (Flyway, schema `postflow`, `ddl-auto=validate`, UTC)
- AI: `LLMProvider` 추상화 (기본 구현 Claude / 교체 가능)
- 파일 스토리지: `StorageService` 추상화 (local=파일시스템, prod=S3)

## 구조

```
com.postflow
├─ common/        config(Security, Web/CORS, QueryDsl, JPA auditing), entity, web
├─ ai/            LLMProvider, ModelTier, dto, provider/ClaudeProvider
├─ storage/       StorageService, Local/S3 구현
├─ user/ social/ post/ analytics/ aigeneration/   도메인 엔티티
```

## 로컬 실행

전제: 로컬 PostgreSQL(`postflow`/`postflow`)과 Redis 기동.

```bash
./gradlew bootRun          # 기본 프로파일 local
```

빌드 / 패키징:

```bash
./gradlew build            # 테스트 포함
./gradlew bootJar          # 실행 가능 jar
```

## 배포

- `Dockerfile`은 내부 8080 expose. HTTPS·도메인 라우팅은 서버의 **Caddy**가 담당.
- `docker-compose.yml` / `Caddyfile`은 **서버에서 관리, repo 미포함** (전역 규약).
- prod 시크릿은 env로 주입 (디폴트 없음): `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`,
  `REDIS_HOST`, `AWS_S3_BUCKET`, `AWS_REGION`, `AWS_S3_PUBLIC_BASE_URL`, `ANTHROPIC_API_KEY` 등.

## 현재 상태 (스캐폴딩)

골격 + 추상화 + 엔티티/마이그레이션까지 완료, 빌드 통과. 미구현:

- ClaudeProvider ↔ Anthropic SDK 실제 연동
- Google OAuth 로그인 / 인증 플로우
- Threads OAuth·발행·토큰 갱신·스케줄러
- 도메인 서비스/컨트롤러/리포지토리, 분석 수집
