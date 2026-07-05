# API 키 발급 가이드

PostFlow가 자체적으로 발급하는 외부 키는 **Anthropic(AI)** 과 **Threads(Meta)** 둘뿐이다.
로그인·구독은 synub 통합 시스템(SSO·중앙 빌링)에 붙어서 처리하고, 이 앱은 **연동 값(서비스 키·웹훅 시크릿)** 만 공유받는다.
키가 없어도 앱은 뜨고 해당 기능만 "설정 필요/에러"로 처리되며, **채우면 바로 활성화**된다.

| 키 | 활성화되는 기능 | 들어가는 곳 |
|---|---|---|
| Anthropic API Key | AI 콘텐츠/시리즈 생성 | `apps/api/.env` → `ANTHROPIC_API_KEY` |
| Threads(Meta) App ID/Secret | Threads 연결·발행·분석 | `apps/api/.env` → `THREADS_APP_ID`, `THREADS_APP_SECRET` |
| (SSO/빌링) 서비스 키·웹훅 시크릿 | 로그인·구독 연동 | `apps/api/.env` → `SERVICE_API_KEY`, `BILLING_WEBHOOK_SECRET` (빌링과 동일 값) |

> **로그인·결제는 이 앱이 직접 발급하지 않는다.** synub 통합 시스템(SSO·중앙 빌링)에 붙어서
> 로그인은 **synub-sso**, 구독·결제는 **synub-billing**이 담당한다(아래 §2·§4). Google/Stripe/Toss 키는 더 이상 쓰지 않는다.

설정 절차: 각 앱의 `.env.example`을 복사(`apps/api/.env`, `apps/web/.env.local`) → 값 입력 →
백엔드 재시작(`./gradlew bootRun`), 프론트는 Vite가 자동 반영(필요 시 재시작).

---

## 1. Anthropic API Key (AI 생성)

1. https://console.anthropic.com 접속 → 로그인/가입.
2. 좌측 **API Keys** (또는 Settings → API Keys) 이동.
3. **Create Key** → 이름 입력(예: `postflow-local`) → 생성.
4. 표시되는 `sk-ant-...` 키를 복사 (다시 볼 수 없으니 바로 저장).
5. **결제 등록 필요**: Settings → Billing에서 크레딧 충전 또는 카드 등록(무료 크레딧 소진 후 호출 실패).
6. `apps/api/.env`에 입력:
   ```
   ANTHROPIC_API_KEY=sk-ant-...
   ```
7. 백엔드 재시작 후 AI 생성 화면에서 실제 카드 생성 확인.

> 모델은 코드에서 tier로 매핑(STANDARD=Sonnet, PREMIUM=Opus, LIGHT=Haiku). 별도 설정 불필요.
> 사용량/비용은 콘솔 Usage에서 확인.

---

## 2. 로그인 = synub 통합계정(SSO) — 별도 키 발급 없음

로그인은 이 앱이 만들지 않는다. **synub-sso**가 발급한 JWT를 이 앱이 **검증만** 한다
(PRODUCT_REGISTRATION §7). 프론트는 synub-sso를 **직접 호출**해 토큰을 받는다.

- 로컬: synub-sso를 `:8090`으로 띄우면 기본값으로 바로 동작(별도 키 불필요).
  데모 계정 `demo@synub.io` / `demo1234`로 로그인 체험.
- 검증 설정(기본값 있음): `SSO_JWKS_URI`(=`:8090/.well-known/jwks.json`), `SSO_ISSUER`(=`https://accounts.synub.io`),
  `SSO_AUDIENCE`(=`synub-postflow`). 프론트는 `VITE_SSO_BASE_URL`(=`http://localhost:8090`).
- **주의**: synub-sso의 CORS 허용 오리진에 이 앱의 프론트 origin(`http://localhost:5173`, 운영 `https://postflow.synub.io`)이
  등록돼 있어야 프론트→SSO 직접 호출이 된다(synub-sso `sso.cors.allowed-origins`).

> 신원의 원천은 synub-sso다. 이 앱 `users` 테이블은 크레덴셜 없이 `external_id`(토큰 sub)로 연결한 로컬 프로필만 보관.

---

## 3. Threads (Meta) App (연결·발행)

> Threads 앱 ID/시크릿은 Facebook 앱과 **별개**다. 한 Meta 앱 안에서 Threads 제품을 추가하면
> Threads 전용 ID/시크릿이 발급된다.

1. https://developers.facebook.com → **My Apps → Create App**.
2. 앱 생성 후 **Add Product**에서 **Threads API** 추가.
3. Threads → **Settings/Basic**에서 **Threads App ID / Threads App Secret** 확인.
4. **Redirect Callback URI** 등록(앱의 Threads 설정 → Redirect URIs):
   - `http://localhost:8080/api/threads/callback` (로컬, 백엔드 포트에 맞춰)
   - (배포 시) `https://api.example.com/api/threads/callback`
5. **권한(스코프)**: `threads_basic`, `threads_content_publish`, `threads_manage_insights`.
   - 본인(개발자/테스터) 계정은 바로 사용 가능. **일반 사용자 공개 운영에는 App Review(Advanced Access)** 필요 — 각 권한별 검수 + 비즈니스 인증 포함, 일정 여유 두기.
6. `apps/api/.env`에 입력:
   ```
   THREADS_APP_ID=...
   THREADS_APP_SECRET=...
   # 콜백/리다이렉트 포트가 다르면:
   # THREADS_REDIRECT_URI=http://localhost:8080/api/threads/callback
   # THREADS_FRONTEND_REDIRECT_URL=http://localhost:5173/settings/threads?threads=connected
   ```
7. 백엔드 재시작 → 설정 > Threads 연결에서 "연결하기" → Meta 동의 → 콜백 → 연결됨 확인.

> ⚠️ **Threads OAuth는 HTTPS redirect URI 필수** (http 로컬은 `error_code 1349187 "안전하지 않은 로그인 차단"`).
> 로컬 테스트 제일 쉬운 법 = **cloudflared 퀵터널**(계정 불필요):
> ```
> brew install cloudflared
> cloudflared tunnel --url http://localhost:8080   # → https://xxxx.trycloudflare.com 발급
> ```
> 1) 발급된 `https://xxxx.trycloudflare.com/api/threads/callback` 을 Threads 앱 Redirect URI에 등록
> 2) `apps/api/.env` 에 `THREADS_REDIRECT_URI=https://xxxx.trycloudflare.com/api/threads/callback` → 백엔드 재시작
> 3) 연결하기(팝업) → 승인 → 연결됨. (트라이클라우드플레어 URL은 재시작마다 바뀜 — 프로덕션은 고정 https 도메인 사용)

> 토큰은 long-lived(60일)로 저장되고 만료 7일 전 자동 갱신된다(서버 cron). refresh_token은 없다.
> 발행 한도: 사용자당 250건/24h. 네이티브 예약 없음 → 서버 스케줄러가 발행 시점에 처리.

---

## 4. 구독·결제 = synub 중앙 빌링 — 이 앱은 상태만 받음

결제/구독은 이 앱이 하지 않는다. **synub-billing**이 카탈로그·결제·구독의 single source of truth다.
이 앱은 **① entitlements 조회(pull) + ② 웹훅 수신(push)** 두 경로로 구독 상태만 받아 게이팅한다.

- **service_code = `post-flow`** (빌링 카탈로그 등록값, 불변). 플랜: Free(총 10개) / Basic(₩15,000·월 50개) / Pro(₩25,000·무제한).
- **① entitlements**: `GET {빌링}/api/entitlements?service=post-flow&customer={sub}` 헤더 `X-Service-Key`.
  → `/auth/me` 호출 시마다 pull해서 로컬 plan 캐시 갱신.
- **② 웹훅 수신**: 빌링이 구독 변화 시 이 앱의 `POST /api/webhooks/billing`으로 POST(`X-Synub-Signature` HMAC).
  빌링 카탈로그의 제품 **webhook_url**을 `https://postflow.synub.io/api/webhooks/billing`로 등록(⚠️ `/api` 프리픽스 필수).
- 필요한 공유 값(빌링과 동일):
  ```
  # apps/api/.env  (로컬은 기본값으로 동작)
  SERVICE_API_KEY=local-service-key            # entitlements 조회 인증(빌링 발급)
  BILLING_WEBHOOK_SECRET=local-dev-webhook-secret   # 웹훅 서명 검증(빌링 app.webhook.secret)
  # BILLING_BASE_URL=https://app.synub.io       # 운영 빌링 주소
  ```
- 플랜 등록/수정은 빌링 repo의 **Flyway 마이그레이션**으로만(PRODUCT_REGISTRATION.md). 이 앱에선 안 만든다.
- 무료(구독 없음)는 이 앱이 **총 10개 누적**으로 게이팅(빌링엔 amount 0 Free 플랜으로 노출).

> 계정 화면의 "구독 관리"/플랜 버튼은 빌링 `/subscriptions`(내 구독)로 링크(`VITE_BILLING_WEB_URL`).

---

## 운영(prod) 추가 키

로컬 기본값이 있는 것들도 prod에서는 반드시 주입:

| env | 용도 |
|---|---|
| `AUTH_JWT_SECRET` | JWT 서명(≥32바이트, 랜덤) |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | PostgreSQL |
| `REDIS_HOST` / `REDIS_PORT` | Redis |
| `AWS_S3_BUCKET` / `AWS_REGION` / `AWS_S3_PUBLIC_BASE_URL` | S3 스토리지 |
| `CORS_ALLOWED_ORIGINS` | 허용 프론트 도메인 |

prod 시크릿은 yml 디폴트 없이 환경변수로만 주입한다.

## 에러 모니터링 (Sentry) — 선택, 미설정 시 완전 비활성

Sentry 프로젝트 2개(프론트/백엔드) 만들어 DSN 주입. **DSN 없으면 no-op**이라 안 넣어도 앱 정상 동작.

| env | 어디 | 용도 |
|---|---|---|
| `SENTRY_DSN` | 백엔드(prod env) | Spring 미처리 예외 → Sentry |
| `SENTRY_ENV` | 백엔드(선택) | 환경명(기본 local, prod yml은 prod 태깅) |
| `VITE_SENTRY_DSN` | 프론트(Vercel env) | React 렌더 오류·JS 예외 → Sentry |
| `VITE_APP_VERSION` | 프론트(선택) | 릴리스 태깅(배포 커밋 해시 등) |

- 프론트: `main.tsx`의 `initSentry()`가 DSN 있을 때만 켜짐 + `Sentry.ErrorBoundary`가 렌더 오류 캐치(친근한 폴백 화면).
- 백엔드: `sentry-spring-boot-starter-jakarta`가 미처리 예외 자동 캡처, traces 10% 샘플링.
