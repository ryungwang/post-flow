# 배포 런북 — post-flow

전역 인프라 규약(shared-dev-system) 기준, **소비자 제품 레퍼런스 = `synub-office`** 와 동일 방식.
**API = Docker→GHCR→공유 Lightsail `~/synub-prod` compose**, **web = Vercel(Vite SPA)**.
`docker-compose.yml`·`Caddyfile`은 **서버 관리(repo 미포함)** — 이 repo엔 Dockerfile + env 계약 + CI만.

## 0. 현황 (준비됨)
- `apps/api/Dockerfile` — 멀티스테이지(JDK21→JRE21), 컨텍스트=`apps/api`(독립 gradle), non-root(uid 10001), `MaxRAMPercentage=75`, TZ=KST. ✅
- `apps/api/src/main/resources/application-prod.yml` — 공유 db의 `synub_postflow`+search_path `postflow`, S3(prefix `synub-postflow`). SSO/빌링/cors/AI/threads는 base yml의 `synub.*`·`${ENV:기본}`(office 컨벤션). ✅
- `.github/workflows/deploy.yml` — main push(apps/api/**) + 수동. GHCR build/push → Lightsail SSH deploy. ✅
- `apps/api/.dockerignore` — build/.gradle/.env 제외. ✅

## 네이밍 (서버 compose와 일치 — 확정)
| 위치 | 값 |
|---|---|
| CI `IMAGE_NAME` (GHCR) | `ghcr.io/<owner>/postflow-api` |
| compose 서비스·container | `postflow-api` |
| prod yml `storage.s3.prefix` | `${AWS_S3_PREFIX:synub-postflow}` (env 기본값 — s3.env엔 AWS_S3_PREFIX 없어 앱 기본값 적용) |
> 서버 `~/synub-prod/docker-compose.yml`: `image: ghcr.io/ryungwang/postflow-api:latest`,
> `container_name: postflow-api`, `env_file: [.env, s3.env]`, `expose: ["8080"]`, `depends_on: [db]`.

## 1. GitHub Secrets
- `LIGHTSAIL_HOST`, `LIGHTSAIL_USER`(예: ubuntu), `LIGHTSAIL_SSH_KEY`(개인키). GHCR는 `GITHUB_TOKEN` 자동.

## 2. API 런타임 env 계약 (서버 compose `env_file`로 주입 — 시크릿은 이미지에 안 넣음)

env 이름·구조는 **office(같은 소비자 제품)와 동일** — `SYNUB_SSO_*`/`SYNUB_BILLING_*`, prefix `synub.sso`/`synub.billing`.
**전 synub 앱이 같은 값을 쓰는 공통** 과 **postflow별로 다른 전용** 을 구분한다. (값·시크릿은 서버 파일이 원천 — repo에 안 박음)

### 2-1. 공통 env — 전 synub 앱 동일 (공용 `.env` + `s3.env`)
| env | 값 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `SERVER_PORT` | `8080` |
| `SYNUB_SSO_BASE_URL` / `SYNUB_SSO_ISSUER` | `https://accounts.synub.io` (JWKS는 base-url에서 파생) |
| `SYNUB_BILLING_BASE_URL` | `https://app-api.synub.io` (빌링 API) |
| `SYNUB_BILLING_SERVICE_KEY` | 빌링 `SERVICE_API_KEY`와 동일 — 전 제품 공유(시크릿) |
| `SYNUB_BILLING_WEBHOOK_SECRET` | 빌링 `app.webhook.secret`과 동일 — 전 제품 공유(시크릿) |
| `AWS_REGION` | `ap-northeast-2` (`s3.env`) |
| `AWS_S3_BUCKET` | `synub-prod-uploads-haru` (`s3.env`) |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | 공유 자격증명 (`s3.env`, 시크릿) |
| `AWS_S3_PUBLIC_BASE_URL` | 업로드 공개 URL — 같은 버킷이라 공통(예: `https://synub-prod-uploads-haru.s3.ap-northeast-2.amazonaws.com`) |

### 2-2. postflow 전용 env — 앱별로 다름
| env | 값 |
|---|---|
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | post-flow 전용 db 유저(`postflow`) — office는 `office` |
| `SYNUB_SSO_AUDIENCE` | `synub-postflow` (앱별 고유) |
| `SYNUB_BILLING_SERVICE_CODE` | `post-flow` (앱별 고유) |
| `APP_CORS_ALLOWED_ORIGINS` | `https://postflow.synub.io` |
| `ANTHROPIC_API_KEY` | AI 생성(이 제품 키) |
| `THREADS_APP_ID` / `THREADS_APP_SECRET` | 이 제품의 Threads 앱 |
| `THREADS_REDIRECT_URI` / `THREADS_FRONTEND_REDIRECT_URL` | Threads 콜백/복귀(고정 https) |
| `AUTH_STATE_SECRET` | Threads OAuth state 서명(≥32B, 로그인용 아님) |

**postflow `.env` 붙여넣기용** (`<...>`만 실제 값으로 채우기):
```dotenv
# --- post-flow 전용 (앱마다 다름) ---
SPRING_DATASOURCE_USERNAME=postflow
SPRING_DATASOURCE_PASSWORD=<postflow DB 비번>
SYNUB_SSO_AUDIENCE=synub-postflow
SYNUB_BILLING_SERVICE_CODE=post-flow
APP_CORS_ALLOWED_ORIGINS=https://postflow.synub.io
ANTHROPIC_API_KEY=<claude api key>
THREADS_APP_ID=<meta threads app id>
THREADS_APP_SECRET=<meta threads app secret>
THREADS_REDIRECT_URI=https://postflow.synub.io/api/threads/callback
THREADS_FRONTEND_REDIRECT_URL=https://postflow.synub.io/settings?threads=connected
AUTH_STATE_SECRET=<32바이트 이상 랜덤>
```
> 공통(`SPRING_PROFILES_ACTIVE`·`SYNUB_SSO_*`·`SYNUB_BILLING_BASE_URL/SERVICE_KEY/WEBHOOK_SECRET`·`AWS_*`)은
> office와 동일 값 → 공용 `.env`+`s3.env`에서 로드(여기 중복 X). `AUTH_STATE_SECRET`은 `openssl rand -base64 32`로 생성.

> **yml 기본값이라 env 불필요**: `SPRING_DATASOURCE_URL`(=`db:5432/synub_postflow`), `SPRING_DATASOURCE_INIT_SQL`(=`search_path TO postflow`), S3 `prefix`(=`synub-postflow`). SSO/빌링 base-url·issuer·audience·service-code도 yml 기본값 있음(운영은 위 env로 명시 권장).

## 3. DB (공유 host 앱별 격리 — office=synub_office, billing=synub_billing 방식)
- 공유 postgres `db`(postgres:16)에 **DB `synub_postflow`** + **스키마 `postflow`** + **post-flow 전용 role `postflow`**(해당 DB/스키마 권한만).
- Flyway가 부팅 시 V1~V25 자동 적용(search_path=postflow). 로컬 PG 버전 = prod(16) 맞출 것.

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
  `https://postflow.synub.io/api/webhooks/billing`**로 등록(⚠️ `/api` 프리픽스 필수). entitlements는 `SYNUB_BILLING_SERVICE_KEY`로 pull.
- **Threads(Meta)**: 앱 Redirect URI에 `https://postflow.synub.io/api/threads/callback` 등록.

## 6. 배포
```bash
# 자동: apps/api/** 변경을 main에 push → deploy.yml 실행
# 수동: Actions → deploy → Run workflow
# 서버에서 실행: cd ~/synub-prod && docker compose pull postflow-api && up -d postflow-api && up -d --force-recreate caddy
curl https://postflow.synub.io/api/actuator/health   # {"status":"UP"}
```
