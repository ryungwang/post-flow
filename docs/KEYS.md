# API 키 발급 가이드

PostFlow를 실제로 동작시키려면 3종의 외부 키가 필요하다. 키가 없어도 앱은 뜨고 해당
기능만 "설정 필요/에러"로 처리되며, **키를 채우면 바로 활성화**된다.

| 키 | 활성화되는 기능 | 들어가는 곳 |
|---|---|---|
| Anthropic API Key | AI 콘텐츠/시리즈 생성 | `apps/api/.env` → `ANTHROPIC_API_KEY` |
| Google OAuth Client ID | Google 로그인 | `apps/api/.env` → `GOOGLE_CLIENT_ID` + `apps/web/.env.local` → `VITE_GOOGLE_CLIENT_ID` (동일 값) |
| Threads(Meta) App ID/Secret | Threads 연결·발행·분석 | `apps/api/.env` → `THREADS_APP_ID`, `THREADS_APP_SECRET` |
| Stripe(PG) Secret/Webhook/Price | 유료 플랜 결제·구독 | `apps/api/.env` → `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `STRIPE_PRICE_*` |

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

## 2. Google OAuth Client ID (로그인)

1. https://console.cloud.google.com 접속 → 프로젝트 생성/선택.
2. **APIs & Services → OAuth consent screen** 설정:
   - User Type: **External** → 만들기.
   - 앱 이름/지원 이메일 입력, 저장.
   - **Test users**에 로그인할 Gmail 주소 추가(게시 전 테스트 모드에서 필수).
3. **APIs & Services → Credentials → Create Credentials → OAuth client ID**:
   - Application type: **Web application**
   - Name: `postflow-web`
   - **Authorized JavaScript origins**에 추가:
     - `http://localhost:5173`
     - (배포 시) 실제 프론트 도메인 `https://app.example.com`
   - (이 ID토큰 방식은 redirect URI 불필요 — 비워도 됨)
   - 만들기 → **Client ID** (`...apps.googleusercontent.com`) 복사.
4. 동일 값을 두 곳에 입력:
   ```
   # apps/api/.env
   GOOGLE_CLIENT_ID=xxxx.apps.googleusercontent.com
   ```
   ```
   # apps/web/.env.local
   VITE_GOOGLE_CLIENT_ID=xxxx.apps.googleusercontent.com
   ```
5. 백엔드 재시작 + 프론트 재시작 → 로그인 화면의 Google 버튼으로 로그인 확인.

> 백엔드는 이 Client ID를 ID토큰 **audience 검증**에 사용하므로 FE/BE 값이 **반드시 동일**해야 한다.
> Client Secret은 이 방식(ID 토큰 검증)에서는 사용하지 않는다.

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

## 4. Stripe (PG) — 유료 플랜 결제·구독

> 결제는 **PaymentProvider 추상화 + Stripe 구현**이라, 키가 없으면 결제 비활성(로컬은 즉시 전환으로 테스트)되고
> **키를 채우면 실제 구독 결제가 동작**한다. Toss 등 다른 PG는 같은 인터페이스 구현으로 교체 가능.

### 4-1. 계정·키 발급
1. https://dashboard.stripe.com → 가입/로그인 (사업자/개인 모두 가능, 테스트 모드는 즉시 사용).
2. 우상단 **테스트 모드** 토글로 시작 권장(`sk_test_...`). 실서비스 전환 시 라이브 키(`sk_live_...`)로 교체.
3. **Developers → API keys**에서 **Secret key** 복사 → `STRIPE_SECRET_KEY`.
   - Publishable key는 이 서버 연동(Checkout 리다이렉트 방식)에서는 불필요.

### 4-2. 상품·가격(Price) 만들기 — 플랜별 1개씩
1. **Product catalog → Add product** 로 플랜 3개 생성: Starter / Pro / Business.
2. 각 상품에 **반복 결제(Recurring) 가격**을 추가(월간, 통화 KRW 등):
   - Starter ₩9,900/월, Pro ₩29,000/월, Business ₩49,000/월 (원하는 금액으로).
3. 생성된 각 가격의 **Price ID**(`price_...`) 복사 → 아래 env에 매핑.
   ```
   STRIPE_PRICE_STARTER=price_...
   STRIPE_PRICE_PRO=price_...
   STRIPE_PRICE_BUSINESS=price_...
   ```
   > Free는 결제 상품이 아니므로 Price 불필요.

### 4-3. 웹훅(결제·구독 이벤트 수신) 등록
1. **Developers → Webhooks → Add endpoint**.
2. **Endpoint URL**: `https://<백엔드 도메인>/api/billing/webhook`
   - 로컬 테스트는 Stripe CLI 사용: `stripe listen --forward-to localhost:8080/api/billing/webhook` → 출력되는 `whsec_...` 사용.
3. 구독할 **이벤트 선택**:
   - `checkout.session.completed` (업그레이드 확정)
   - `customer.subscription.updated` (취소 예약/재개)
   - `customer.subscription.deleted` (기간 종료 → 무료 강등)
4. 생성 후 표시되는 **Signing secret**(`whsec_...`) 복사 → `STRIPE_WEBHOOK_SECRET`.
   > 서명 검증 + 이벤트 ID 멱등 처리로 재전송/위조를 막는다.

### 4-4. .env 입력 + 확인
```
# apps/api/.env
STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
STRIPE_PRICE_STARTER=price_...
STRIPE_PRICE_PRO=price_...
STRIPE_PRICE_BUSINESS=price_...
```
1. 백엔드 재시작.
2. 계정 화면 → 플랜에서 **업그레이드** → Stripe Checkout으로 이동 → 테스트 카드(`4242 4242 4242 4242`, 미래 만료일, 임의 CVC)로 결제.
3. 결제 완료 시 웹훅으로 플랜이 자동 반영되는지 확인. **구독 취소·관리**는 같은 화면의 버튼 → Stripe Billing Portal에서 처리.

> 키 미설정(로컬)일 때는 업그레이드/취소가 DB에서 즉시 처리되어 게이팅을 테스트할 수 있다(실결제 아님).
> 결제한 기간은 끝까지 유지되고, 기간 종료 시 `subscription.deleted` 웹훅(또는 안전망 잡)으로 무료 전환된다.

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
