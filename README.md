# PostFlow API

Create Once. Grow Automatically. — AI 콘텐츠 자동 생성·예약·발행 플랫폼의 백엔드.

자세한 제품 사양은 [`prd.md`](./prd.md) 참조.

## Tech Stack

- Java 21 · Spring Boot 3.4 · Spring Security · JPA · QueryDSL · Redis
- PostgreSQL (Flyway, schema `postflow`, `ddl-auto=validate`, UTC)
- AI: `LLMProvider` 추상화 (기본 구현 Claude / 교체 가능)
- 파일 스토리지: `StorageService` 추상화 (local=파일시스템, prod=S3)

## 모노레포 구조

```
post-flow/
├─ apps/
│  ├─ api/        Spring Boot 백엔드 (Gradle, 자체 빌드)
│  └─ web/        React/Vite 프론트엔드 (예정, npm)
├─ prd.md         제품 사양
└─ README.md
```

각 앱은 자체 빌드(api=Gradle, web=npm)인 폴리글랏 모노레포. 루트 Gradle 통합 없음.

### apps/api 패키지

```
com.postflow
├─ common/        config(Security, Web/CORS, QueryDsl, JPA auditing), entity, web
├─ ai/            LLMProvider, ModelTier, dto, provider/ClaudeProvider, content/
├─ storage/       StorageService, Local/S3 구현
├─ user/ social/ post/ analytics/ aigeneration/   도메인 엔티티
```

## 로컬 실행 (apps/api)

전제: 로컬 PostgreSQL(`postflow`/`postflow`)과 Redis 기동.

```bash
cd apps/api
./gradlew bootRun          # 기본 프로파일 local
./gradlew build            # 테스트 포함
./gradlew bootJar          # 실행 가능 jar
```

## 환경변수 / 키 (나중에 채우면 바로 작동)

키가 없어도 앱은 뜨고, 해당 기능은 "설정 필요"·에러 처리로 동작한다. 키만 채우면 그 기능이 활성화된다.

**백엔드** — `apps/api/.env.example` → `apps/api/.env` 로 복사 후 값 입력 (Spring이 자동 로드):

| 키 | 기능 |
|---|---|
| `ANTHROPIC_API_KEY` | AI 콘텐츠 생성 (`/api/ai/generate`) |
| `GOOGLE_CLIENT_ID` | Google 로그인 ID토큰 검증 (프론트와 동일 값) |
| `THREADS_APP_ID` / `THREADS_APP_SECRET` | Threads 연동·발행 |
| `AUTH_JWT_SECRET` (선택) | JWT 서명 (prod 필수) |

**프론트** — `apps/web/.env.example` → `apps/web/.env.local` 로 복사 (Vite 자동 로드):

| 키 | 기능 |
|---|---|
| `VITE_GOOGLE_CLIENT_ID` | Google 로그인 버튼 (백엔드와 동일 값) |
| `VITE_API_PROXY_TARGET` | API 프록시 대상 (기본 `http://localhost:8080`) |

> Google 로그인은 FE/BE **동일 Client ID** 필요 + Google Cloud OAuth 클라이언트의 Authorized JS origin 에 `http://localhost:5173` 등록.

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
