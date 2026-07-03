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
  --build-arg VITE_API_BASE_URL=https://postflow.synub.io/api \
  --build-arg VITE_SSO_BASE_URL=https://accounts.synub.io \
  --build-arg VITE_BILLING_WEB_URL=https://app.synub.io .
```
> ⚠️ web의 `VITE_*`는 **빌드 타임에 박힌다**. 도메인/SSO 주소가 바뀌면 web 이미지를 다시 빌드.

## 2. API 환경변수 계약 (prod, 디폴트 없음 = 없으면 기동 실패)

| env | 용도 |
|---|---|
| `SPRING_PROFILES_ACTIVE=prod` | prod 프로필 |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | PostgreSQL (`jdbc:postgresql://host:5432/postflow`) |
| `REDIS_HOST` / `REDIS_PORT` | Redis |
| `AWS_S3_BUCKET` / `AWS_REGION` / `AWS_S3_PUBLIC_BASE_URL` | S3 스토리지 (+선택 `AWS_S3_PREFIX`) |
| `CORS_ALLOWED_ORIGINS=https://postflow.synub.io` | 허용 프론트 도메인 |
| `ANTHROPIC_API_KEY` | AI 생성 |
| `THREADS_APP_ID` / `THREADS_APP_SECRET` | Threads |
| `THREADS_REDIRECT_URI=https://postflow.synub.io/api/threads/callback` | Threads 콜백(고정 https) |
| `THREADS_FRONTEND_REDIRECT_URL=https://postflow.synub.io/settings/threads?threads=connected` | 연결 후 복귀 |
| `AUTH_STATE_SECRET` | Threads OAuth state 서명(≥32B, 로그인용 아님) |
| `SSO_ISSUER` / `SSO_JWKS_URI` | synub-sso 토큰 검증 (기본 accounts.synub.io) |
| `BILLING_BASE_URL=https://app.synub.io` | synub 중앙 빌링 주소 |
| `SERVICE_API_KEY` | 빌링 entitlements 조회 인증(빌링 발급, 동일 값) |
| `BILLING_WEBHOOK_SECRET` | 빌링 웹훅 서명 검증(빌링 `app.webhook.secret`과 동일) |

> 로그인/결제는 이 앱이 발급하지 않는다 — **synub-sso**(로그인)·**synub-billing**(구독)에 붙는다.

## 연동 연결 (synub 중앙 시스템)
- **로그인(SSO)**: 프론트가 synub-sso를 직접 호출 → 토큰. **synub-sso `sso.cors.allowed-origins`에
  `https://postflow.synub.io` 등록** 필요. 백엔드는 JWKS로 검증만(audience `synub-postflow`).
- **구독(빌링)**: service_code=`post-flow`. 빌링 카탈로그의 제품 **webhook_url을
  `https://postflow.synub.io/api/webhooks/billing`**로 등록(⚠️ `/api` 프리픽스). entitlements는 `SERVICE_API_KEY`로 pull.
- 자세한 계약: `docs/KEYS.md` §2·§4.

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
