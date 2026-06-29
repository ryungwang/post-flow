# 배포 가이드 (AWS Lightsail + Caddy + S3)

PostFlow는 컨테이너 2개(api·web)로 배포한다. **HTTPS·도메인 라우팅은 서버의 Caddy**가 처리하고,
앱 컨테이너는 내부 8080만 노출한다. `docker-compose.yml`·`Caddyfile`은 **서버에서 관리(레포 미포함)** —
아래는 그 레퍼런스다.

## 0. 사전 준비 (사용자)
- AWS Lightsail 인스턴스(Docker), 고정 IP
- 도메인 2개: `app.example.com`(web), `api.example.com`(api)  ← DNS A레코드 → 고정 IP
- S3 버킷(공개 읽기 또는 CloudFront) + 인스턴스 IAM 롤(또는 `AWS_*` 자격증명)
- PostgreSQL·Redis (RDS/ElastiCache 또는 같은 compose에 컨테이너)

## 1. 이미지 빌드
```bash
# api (context = apps/api)
docker build -f apps/api/Dockerfile -t postflow-api apps/api
# web (context = repo root, 빌드시 공개 env 주입 — Vite가 인라인)
docker build -f apps/web/Dockerfile -t postflow-web \
  --build-arg VITE_API_BASE_URL=https://api.example.com/api \
  --build-arg VITE_GOOGLE_CLIENT_ID=xxxx.apps.googleusercontent.com .
```
> ⚠️ web의 `VITE_*`는 **빌드 타임에 박힌다**. 도메인/구글 클라이언트ID가 바뀌면 web 이미지를 다시 빌드.

## 2. API 환경변수 계약 (prod, 디폴트 없음 = 없으면 기동 실패)
| env | 용도 |
|---|---|
| `SPRING_PROFILES_ACTIVE=prod` | prod 프로필 |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | PostgreSQL (`jdbc:postgresql://host:5432/postflow`) |
| `REDIS_HOST` / `REDIS_PORT` | Redis |
| `AUTH_JWT_SECRET` | JWT 서명 (≥32바이트 랜덤) |
| `AWS_S3_BUCKET` / `AWS_REGION` / `AWS_S3_PUBLIC_BASE_URL` | S3 스토리지 (+선택 `AWS_S3_PREFIX`) |
| `CORS_ALLOWED_ORIGINS=https://app.example.com` | 허용 프론트 도메인 |
| `GOOGLE_CLIENT_ID` | 구글 로그인(FE와 동일 값) |
| `ANTHROPIC_API_KEY` | AI 생성 |
| `THREADS_APP_ID` / `THREADS_APP_SECRET` | Threads |
| `THREADS_REDIRECT_URI=https://api.example.com/api/threads/callback` | Threads 콜백(고정 https) |
| `THREADS_FRONTEND_REDIRECT_URL=https://app.example.com/settings/threads?threads=connected` | 연결 후 복귀 |
| `BILLING_SYNC_SECRET` | 외부 결제 사이트 → 내부 plan 동기화 공유 시크릿(없으면 동기화 API 비활성) |
| `PAYMENT_PROVIDER` / `TOSS_*` / `STRIPE_*` | (선택) 인앱 결제용 — 현재 인앱 결제 UI는 숨김(`IN_APP_BILLING=false`), 결제는 별도 사이트 |

## 결제 연동 (별도 결제 사이트 → 이 앱)
인앱 결제 UI는 숨겨져 있고(`apps/web/src/lib/billing-config.ts` → `IN_APP_BILLING`), 결제는 **별도 결제 사이트**가 처리한다.
결제/취소 후 결제 사이트가 아래 내부 API로 plan을 동기화한다(서버↔서버, 공유 시크릿).

```
POST https://api.example.com/api/internal/billing/plan
Header: X-Internal-Token: <BILLING_SYNC_SECRET>
Body(JSON): { "userId": 1 | "email": "user@x.com",   // 둘 중 하나
              "plan": "STARTER|PRO|BUSINESS",          // ACTIVATE 시 필수
              "action": "ACTIVATE|SCHEDULE_CANCEL|CANCEL",
              "periodEnd": "2026-12-31T00:00:00Z" }    // 선택(기본 now+30일)
```
- `ACTIVATE`: 유료 플랜 활성(기간 설정) · `SCHEDULE_CANCEL`: 기간 말 취소 예약 · `CANCEL`: 즉시 FREE
- 미인증=401, 사용자 없음=404, 시크릿 미설정=503

## 3. 서버 docker-compose.yml (레퍼런스 — 서버에서 관리)
```yaml
services:
  api:
    image: postflow-api
    env_file: [./api.env]      # 위 2번 계약
    expose: ["8080"]
    restart: unless-stopped
  web:
    image: postflow-web
    expose: ["8080"]
    restart: unless-stopped
  caddy:
    image: caddy:2-alpine
    ports: ["80:80", "443:443"]
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile
      - caddy_data:/data
    restart: unless-stopped
volumes: { caddy_data: {} }
```

## 4. 서버 Caddyfile (레퍼런스 — 자동 HTTPS)
```
app.example.com {
    reverse_proxy web:8080
}
api.example.com {
    reverse_proxy api:8080
}
```
> 새 도메인은 블록 한 줄 추가 → Caddy가 Let's Encrypt 인증서 자동 발급/갱신.

## 5. 기동 + 확인
```bash
docker compose up -d
curl https://api.example.com/actuator/health   # {"status":"UP"}
# 브라우저로 https://app.example.com 접속 → 구글 로그인 → 생성/발행 확인
```

## 6. 외부 콘솔 등록 (도메인 확정 후)
- **Google OAuth**: Authorized JavaScript origins에 `https://app.example.com` 추가
- **Threads(Meta)**: Redirect URI에 `https://api.example.com/api/threads/callback` 추가
- **Toss**: successUrl/웹훅 URL을 `https://app.example.com/billing/toss` / `https://api.example.com/api/billing/toss/webhook`
- Flyway는 기동 시 자동 마이그레이션(V1~V24) 적용
