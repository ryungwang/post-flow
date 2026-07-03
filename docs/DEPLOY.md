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

## 2. API 런타임 env 계약 (서버 compose가 주입 — 시크릿은 이미지에 안 넣음)
| env | 용도 |
|---|---|
| `SPRING_PROFILES_ACTIVE=prod` | prod 프로필 |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | 공유 `db`의 **postflow 전용 role**(슈퍼유저 금지) |
| `SPRING_DATASOURCE_URL`(선택) | 기본 `jdbc:postgresql://db:5432/postflow` |
| `CORS_ALLOWED_ORIGINS` | 허용 프론트(예: `https://postflow.synub.io`) |
| `ANTHROPIC_API_KEY` | AI 생성 |
| `THREADS_APP_ID` / `THREADS_APP_SECRET` | Threads 연동·발행 |
| `THREADS_REDIRECT_URI` / `THREADS_FRONTEND_REDIRECT_URL` | Threads 콜백/복귀(고정 https) |
| `AUTH_STATE_SECRET` | Threads OAuth state 서명(≥32B, 로그인용 아님) |
| `SSO_ISSUER` / `SSO_JWKS_URI` / `SSO_AUDIENCE` | synub-sso 토큰 검증(기본 accounts.synub.io / `synub-postflow`) |
| `BILLING_BASE_URL` | synub 중앙 빌링(예: `https://app.synub.io`) |
| `SERVICE_API_KEY` | 빌링 entitlements 조회 인증(빌링 발급, 동일 값) |
| `BILLING_WEBHOOK_SECRET` | 빌링 웹훅 서명 검증(빌링 `app.webhook.secret`과 동일) |
| `AWS_S3_BUCKET` / `AWS_REGION` / `AWS_S3_PUBLIC_BASE_URL` | 업로드 스토리지(자격증명=인스턴스 롤) |

## 3. DB (공유 host 앱별 격리)
- 공유 postgres `db`에 **DB `postflow`** + **스키마 `postflow`** + **전용 role**(해당 DB/스키마 권한만).
- Flyway가 부팅 시 V1~V25 자동 적용. 로컬 PG 버전 = prod 버전 맞출 것.

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
