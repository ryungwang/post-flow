# 배포 런북 — post-flow

전역 인프라 규약(shared-dev-system) 기준, synub-billing 레퍼런스와 동일 방식.
**API = Docker→GHCR→공유 Lightsail `~/synub-prod` compose**, **web = Vercel(Vite SPA)**.
`docker-compose.yml`·`Caddyfile`은 **서버 관리(repo 미포함)** — 이 repo엔 Dockerfile + env 계약 + CI만.

## 0. 현황 (준비됨)
- `apps/api/Dockerfile` — 멀티스테이지(JDK21→JRE21), 컨텍스트=`apps/api`(독립 gradle), non-root(uid 10001), `MaxRAMPercentage=75`, TZ=KST. ✅
- `apps/api/src/main/resources/application-prod.yml` — `${ENV}`(시크릿 디폴트 없음), 공유 db(postflow)+search_path, S3(prefix `synub-postflow`). 나머지(SSO/빌링/cors/AI/threads)는 base yml의 `${ENV:기본}`. ✅
- `.github/workflows/deploy.yml` — main push(apps/api/**) + 수동. GHCR build/push → Lightsail SSH deploy. ✅
- `apps/api/.dockerignore` — build/.gradle/.env 제외. ✅

## 네이밍 (서버 compose와 일치 — 확정)
| 위치 | 값 |
|---|---|
| CI `IMAGE_NAME` (GHCR) | `ghcr.io/<owner>/postflow-api` |
| compose 서비스·container | `postflow-api` |
| prod yml `storage.s3.prefix` | `synub-postflow` (하드코딩 — 공유 s3.env가 안 덮게) |
> 서버 `~/synub-prod/docker-compose.yml`: `image: ghcr.io/ryungwang/postflow-api:latest`,
> `container_name: postflow-api`, `env_file: [.env, s3.env]`, `expose: ["8080"]`, `depends_on: [db]`.

## 1. GitHub Secrets
- `LIGHTSAIL_HOST`, `LIGHTSAIL_USER`(예: ubuntu), `LIGHTSAIL_SSH_KEY`(개인키). GHCR는 `GITHUB_TOKEN` 자동.

## 2. API 런타임 env 계약 (서버 compose `env_file`로 주입 — 시크릿은 이미지에 안 넣음)

서버 compose는 `env_file: [.env, s3.env]`. **공통(전 synub 앱 공유)** 과 **postflow 전용**을 나눠 관리한다.

### 2-1. 공통 env — 전 synub 앱 공유 (서버 `~/synub-prod`의 `.env` + `s3.env`)
여러 제품이 같은 값을 쓰므로 **한 곳에서 관리**하고 각 서비스가 함께 로드한다. (실값·시크릿은 서버 파일이 원천 — repo에 안 박음)

**공용 `.env`** (전 앱):
| env | 값/용도 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `SERVER_PORT` | `8080` |
| `SSO_ISSUER` | `https://accounts.synub.io` |
| `SSO_JWKS_URL` | `https://accounts.synub.io/.well-known/jwks.json` (⚠️ `URL`, `URI` 아님) |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | 공용 db 접속 유저(`synub`) — 앱 격리는 DB명으로(§2-2) |
| `SSO_PROVISION_KEY`, `SSO_DEMO_*`, `APP_ADMIN_*` | SSO/관리자용(post-flow는 안 씀, 로드만) |

**공용 `s3.env`**:
| env | 값/용도 |
|---|---|
| `AWS_REGION` | `ap-northeast-2` |
| `AWS_S3_BUCKET` | `synub-prod-uploads-haru` (공유 버킷) |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | 자격증명(시크릿) — DefaultCredentialsProvider가 env로 읽음 |

> - S3 prefix는 env로 안 받는다 — prod yml에 `synub-postflow`로 **하드코딩**(공유 `s3.env`의 `AWS_S3_PREFIX`가 덮어 다른 앱과 섞이는 것 방지).
> - **빌링 연동값(`SERVICE_API_KEY`·`BILLING_WEBHOOK_SECRET`·`BILLING_BASE_URL`)은 전 제품 공유지만 아직 공용 `.env`에 없음 → 추가 필요**(빌링 `app.webhook.secret`/서비스키와 동일 값).

### 2-2. postflow 전용 env
postflow만 쓰는 값 — postflow 서비스의 `environment:`(또는 전용 env 파일)로 주입.

| env | 값/용도 |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://db:5432/postflow` (공용 유저 `synub`가 이 DB에 접속 — 앱 격리) |
| `SSO_AUDIENCE` | `synub-postflow` (이 제품 토큰 audience, 앱별 고유) |
| `CORS_ALLOWED_ORIGINS` | `https://postflow.synub.io` |
| `AWS_S3_PUBLIC_BASE_URL` | 업로드 공개 URL(예: `https://synub-prod-uploads-haru.s3.ap-northeast-2.amazonaws.com`) |
| `ANTHROPIC_API_KEY` | AI 생성(이 제품 키) |
| `THREADS_APP_ID` / `THREADS_APP_SECRET` | 이 제품의 Threads 앱 |
| `THREADS_REDIRECT_URI` / `THREADS_FRONTEND_REDIRECT_URL` | Threads 콜백/복귀(고정 https) |
| `AUTH_STATE_SECRET` | Threads OAuth state 서명(≥32B, 로그인용 아님) |
| `SERVICE_API_KEY` / `BILLING_WEBHOOK_SECRET` | 빌링 연동(공용값이면 §2-1로 이동) |

## 3. DB (공유 host 앱별 격리)
- 공유 postgres `db`(postgres:16)에 **DB `postflow`** + **스키마 `postflow`** 생성.
- 접속 유저는 공용 `synub`(공용 `.env`의 `SPRING_DATASOURCE_USERNAME`) — 이 유저가 `postflow` DB에 접근하도록 **grant** 필요.
- Flyway가 부팅 시 V1~V25 자동 적용. 로컬 PG 버전 = prod(16) 맞출 것.

## 4. Web (Vercel — Vite SPA)
- Vercel 프로젝트 **Root Directory = `apps/web`** (install/build override 금지 — 루트 lockfile 워크스페이스 해석).
- Build: `vite build`, Output: `dist` (Vite 프리셋 자동).
- 빌드타임 env(VITE_*는 번들에 박힘 — 바뀌면 재배포):
  - `VITE_API_BASE_URL=https://postflow.synub.io/api`
  - `VITE_SSO_BASE_URL=https://accounts.synub.io`  ← 로그인은 프론트가 SSO 직접 호출
  - `VITE_BILLING_WEB_URL=https://app.synub.io`  ← "구독 관리" 링크
- ⚠️ **synub-sso CORS 허용 오리진**에 `https://postflow.synub.io` 등록돼 있어야 프론트→SSO 직접 호출 가능.

## 5. synub 연동 연결 (도메인 확정 후)
- **로그인(SSO)**: 위 CORS 등록. 백엔드는 JWKS로 검증만(audience `synub-postflow`).
- **구독(빌링)**: service_code=`post-flow`. 빌링 카탈로그의 제품 **webhook_url을
  `https://postflow.synub.io/api/webhooks/billing`**로 등록(⚠️ `/api` 프리픽스 필수). entitlements는 `SERVICE_API_KEY`로 pull.
- **Threads(Meta)**: 앱 Redirect URI에 `https://postflow.synub.io/api/threads/callback` 등록.

## 6. 배포
```bash
# 자동: apps/api/** 변경을 main에 push → deploy.yml 실행
# 수동: Actions → deploy → Run workflow
# 서버에서 실행: cd ~/synub-prod && docker compose pull postflow-api && up -d postflow-api && up -d --force-recreate caddy
curl https://postflow.synub.io/api/actuator/health   # {"status":"UP"}
```
