# PostFlow — SNS 플랫폼 확장 로드맵

> PostFlow는 **텍스트 콘텐츠**를 AI로 생성한다. 따라서 **텍스트 우선 플랫폼**이 궁합이 좋고,
> 이미지/영상 전용 플랫폼(Instagram·TikTok)은 **미디어 생성 기능이 선행**돼야 한다.
> 진행 원칙: **무료 API부터 차근차근** → 유료·무거운 심사는 뒤로.

_최종 업데이트: 2026-07-11 · 현재: **Threads·Bluesky·Mastodon 완료** · **LinkedIn·Facebook·Instagram 구현(라이브는 크레덴셜/검수 대기)** · X = 유료 보류 · AI 이미지 생성 = 유료 결정 필요_

---

## 우선순위 매트릭스

| 순위 | 플랫폼 | 콘텐츠 궁합 | API 난이도 | 비용 | App Review | 상태 |
|---|---|---|---|---|---|---|
| — | **Threads** | 텍스트 ✅ | — | 무료 | 진행중(Advanced) | ✅ 완료 |
| 1 | **Bluesky** | 텍스트 ✅ | 매우 쉬움 | 무료 | 없음 | ✅ **완료** (연결·발행·이미지·삭제·내게시물/인사이트) |
| 2 | **LinkedIn** | 텍스트 ✅ | 중 | 무료 | 있음(가벼움) | 🟡 **연결·발행(텍스트+이미지)·삭제 구현** (라이브 OAuth는 크레덴셜 대기) |
| 3 | **X (Twitter)** | 텍스트 ✅ | 중 | **유료($100+/월)** | 있음 | ⬜ 보류(비용 결정) |
| 4 | **Facebook 페이지** | 텍스트 ✅ | 중 | 무료 | 있음 | 🟡 **연결·발행(텍스트+이미지)·삭제 구현** (라이브는 검수·크레덴셜 대기) |
| 5 | **Instagram** | 이미지 ❌(선행필요) | 높음 | 무료 | 무거움 | 🟡 **발행 통합 구현**(컨테이너→publish, 이미지 필수) — 라이브는 검수+**AI 이미지 생성(유료·결정 필요)** 대기 |
| — | **Mastodon** | 텍스트 ✅ | 쉬움 | 무료 | 없음 | ✅ **완료** (연결·발행 텍스트+이미지·삭제) — 인스턴스+액세스토큰 |
| — | TikTok / YouTube | 영상 ❌ | 높음 | 무료 | 무거움 | 보류(큰 미스매치) |

---

## Phase 0 — 멀티플랫폼 추상화 (선행 필수) — ✅ 완료 (2026-07-09)

지금은 전부 Threads 전용이라 일반화가 먼저다.

> **구현됨**: `Publisher` 인터페이스 + `PublisherRegistry`(provider별 발행 라우팅), `ThreadsPublisher`/`BlueskyPublisher`,
> `SocialAccount` 범용화(`external_id`·`handle`·`refresh_token`, V26), `/social/*` 엔드포인트(채널목록·연결·기본·해제),
> 채널 게이팅·사이드바 잠금 크로스-프로바이더. **남음(증분2)**: 발행 다중선택 팬아웃(한 글=현재 1채널).

**백엔드**
- `Platform` enum: `THREADS, BLUESKY, LINKEDIN, X, INSTAGRAM, FACEBOOK`
- `SocialAccount`에 `platform` 컬럼 추가(기존 데이터 = THREADS 백필). unique = (userId, platform, externalId)
- `Publisher` 인터페이스 — `connectUrl / exchangeCode / publish(text, media) / fetchPosts / insights` 추상.
  플랫폼별 구현: `ThreadsPublisher`(기존 로직 이관), `BlueskyPublisher` …
- 발행 파이프라인: Post → **대상 플랫폼 다중 선택** → 각 Publisher로 팬아웃(부분 실패 허용)
- OAuth 콜백 라우팅을 플랫폼별로(`/threads/callback` → `/social/{platform}/callback` 일반화, 기존 경로 유지)

**프론트**
- 계정 연결 화면을 플랫폼 목록으로 확장(플랫폼별 연결 버튼/상태)
- 발행 시 **채널(플랫폼×계정) 다중 선택** UI
- 다중계정 게이팅(`canMultiAccount`)을 "채널 수" 기준으로 일반화

**게이팅/카탈로그**
- "N개 채널 연동"의 채널 = 플랫폼×계정. Free=1, Pro=5 유지
- 카탈로그 문구: "Threads·Instagram·X·LinkedIn·Bluesky 멀티 채널"로 갱신(synub-billing, 형)

---

## Phase 1 — Bluesky (최속 검증) — ✅ 완료 (2026-07-09, 실계정 발행 E2E 검증)

- **AT Protocol** · 인증 = **앱 패스워드**(핸들+앱비번, OAuth 불필요). 백엔드는 XRPC HTTP 직접 호출(자바)
- **세션 토큰만 저장**(앱비번 미저장) — `createSession`→access/refresh JWT, 만료 시 `refreshSession` 후 재시도
- 발행: `com.atproto.repo.createRecord`(`app.bsky.feed.post`) → 실제 게시 확인(`synub.bsky.social`에 발행됨)
- **남음(증분2)**: 이미지 발행(blob 업로드), 삭제(`deleteRecord`), 인사이트/게시물 목록(현재 Threads만)

## Phase 2 — LinkedIn (B2B 고가치) — 🟡 연결·발행·삭제 구현 (2026-07-10)

- OAuth2(authorization code) · scope `openid profile w_member_social` · **무료 API**
- 발행: 버전드 REST `POST /rest/posts`(`LinkedIn-Version`+`X-Restli-Protocol-Version:2.0.0`),
  `commentary`는 Little Text 예약문자(`\|{}@[]()<>#*_~`) 이스케이프, 게시물 URN은 `x-restli-id` 응답 헤더에서 획득
- 이미지: 버전드 Images API — `POST /rest/images?action=initializeUpload`(owner=person urn) → 반환 uploadUrl에 바이트 PUT → 게시물 `content.media.id`에 image URN 첨부(Bluesky와 동일한 다운로드 헬퍼 재사용)
- 토큰 만료(401) → refresh_token 재발급 후 1회 재시도(승인 앱만 refresh 발급) → 실패 시 재연결 필요 표시
- 발행은 기존 팬아웃 파이프라인에 자동 편입(`LinkedInPublisher` = `PublisherRegistry` 자동 등록). 삭제도 연동(`DELETE /rest/posts/{urn}`)
- **구현됨**: `/linkedin/connect`·`/linkedin/callback`(OAuth), `LinkedInConnectService`(userinfo→member id 저장), 프론트 채널연결 LinkedInCard(OAuth 팝업)
- **조직(회사 페이지) 발행 = 구현됨(2026-07-11)**: author URN을 person/organization 모두 지원, 연결 시 관리 조직(`organizationAcls`)을 best-effort로 채널 등록(externalId=조직 URN). 활성화는 Community Management API 승인 + `LINKEDIN_SCOPES`에 `r_organization_admin w_organization_social` 추가(미승인 scope는 OAuth 깨지므로 기본 personal만, org 조회는 403→스킵)
- **남음**: 라이브 OAuth E2E(앱 크레덴셜 `LINKEDIN_CLIENT_ID/SECRET` 투입 후 — 텍스트·이미지·조직 함께 검증)
- ⚠️ **개인 프로필 게시물/분석 '읽기' API는 파트너 승인 필요** → Bluesky 같은 "내 게시물/인사이트" 메뉴는 미제공(발행 전용)

## Phase 3 — X (Twitter) (최대 도달, 비용 결정 필요)

- OAuth2 PKCE · API v2 `POST /2/tweets`
- **유료**: Basic $100+/월(쓰기 한도 제한). 무료 티어는 쓰기 극소량 → 사업성 판단 후
- 텍스트 완벽 적합. 비용 승인이 게이트

## Phase 4 — Facebook 페이지 — 🟡 연결·발행·삭제 구현 (2026-07-11)

- OAuth2(Facebook 로그인) · scope `pages_show_list,pages_manage_posts,pages_read_engagement` · **무료**
- 연결: 코드 교환→user token→`GET /me/accounts`로 관리 페이지 목록(각 페이지별 page access token) → 각 페이지를 채널로 upsert(플랜 채널 한도 준수)
- 발행: 텍스트=`POST /{pageId}/feed`(message), 이미지=`POST /{pageId}/photos`(url — 페북이 URL로 가져감, 다운로드 불필요). 게시물 id는 photos의 `post_id` 우선. 삭제=`DELETE /{objectId}`
- 토큰 무효(OAuthException/190)→재연결 필요 표시. `FacebookPublisher`=`PublisherRegistry` 자동 편입
- **구현됨**: `/facebook/connect`·`/facebook/callback`, ConnectService(페이지 목록 upsert·게이팅), 프론트 FacebookCard(OAuth 팝업)
- **남음**: 라이브 OAuth E2E(`FACEBOOK_APP_ID/SECRET` + App Review 승인 후), 여러 페이지 선택 UI

## Phase 5 — Instagram — 🟡 발행 통합 구현 (2026-07-11)

- IG Graph API(페북 Graph 호스트) · IG 비즈니스 계정은 **연결된 FB 페이지에서 감지**(페이지 토큰 공유) · **무료 API**
- 발행: `POST /{igUserId}/media`(image_url+caption로 컨테이너) → `POST /{igUserId}/media_publish`(creation_id). **이미지 필수**(없으면 명확한 에러). 삭제 = IG API 미지원 → no-op
- 연결: Facebook 연결 시 각 페이지의 `instagram_business_account`를 best-effort 감지해 INSTAGRAM 채널 등록. `InstagramPublisher`=`PublisherRegistry` 자동 편입
- **구현됨**: `InstagramApiClient`(discover·container·publish), `InstagramPublisher`, FacebookConnectService IG 등록, PROVIDER_LABEL
- **남음(2가지 게이트)**:
  1. **AI 이미지 생성 기능 = 유료·결정 필요** — 우리 콘텐츠는 텍스트라 IG용 이미지 소스가 없음. 사용자가 이미지 첨부하면 지금도 발행 가능하나, 자동화엔 이미지 생성이 선행. (프로바이더/비용은 미결정 — 유료라 이번 스윕서 제외)
  2. 라이브 OAuth E2E: `FACEBOOK_SCOPES`에 `instagram_basic,instagram_content_publish` 추가 + App Review 승인 후
- ⚠️ Threads API엔 `share_to_instagram` 같은 교차게시 파라미터가 **없음**(별도 IG Graph API 통합 필요) — 확인 완료

---

## 권장 실행 묶음

1. **Phase 0 + Phase 1(Bluesky)** 를 한 묶음으로 — 뼈대 + 첫 확장 동시 증명
2. **Phase 2(LinkedIn)** — 무료 텍스트 플랫폼 하나 더로 멀티채널 가치 완성
3. 이후 **X(비용 결정)**, **Instagram/FB(미디어 생성 후)**

## 열린 결정

- [ ] **AI 이미지 생성 프로바이더·비용** (Instagram 자동발행 선행 + 제품 가치) — 전부 유료라 결정 필요. 후보: OpenAI gpt-image, Google Imagen, Stability 등
- [ ] X API 유료 비용($100+/월) 감수 여부
- [x] ~~Mastodon 포함 여부~~ → 포함·완료(2026-07-11)

## 라이브 활성화 체크리스트 (크레덴셜/검수)

무료 플랫폼 코드는 다 들어갔고, 아래만 채우면 실연결·실발행이 켜진다:
- **LinkedIn**: `LINKEDIN_CLIENT_ID/SECRET`(+ 조직은 Community Management 승인 후 `LINKEDIN_SCOPES`)
- **Facebook**: `FACEBOOK_APP_ID/SECRET` + App Review(pages_manage_posts 등)
- **Instagram**: 위 Facebook + `FACEBOOK_SCOPES`에 instagram 스코프 + IG 비즈니스 계정 + 검수
- **Mastodon**: 인스턴스 액세스 토큰만 있으면 **지금 즉시** (검수·키 불필요)
- **Bluesky**: 이미 실계정 연결·발행 검증됨
- **Threads**: Meta 앱검수 승인 대기(제출 완료)
