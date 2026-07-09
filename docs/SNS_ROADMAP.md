# PostFlow — SNS 플랫폼 확장 로드맵

> PostFlow는 **텍스트 콘텐츠**를 AI로 생성한다. 따라서 **텍스트 우선 플랫폼**이 궁합이 좋고,
> 이미지/영상 전용 플랫폼(Instagram·TikTok)은 **미디어 생성 기능이 선행**돼야 한다.
> 진행 원칙: **무료 API부터 차근차근** → 유료·무거운 심사는 뒤로.

_최종 업데이트: 2026-07-09 · 현재 지원: **Threads (완료) · Bluesky (텍스트 발행 완료)**_

---

## 우선순위 매트릭스

| 순위 | 플랫폼 | 콘텐츠 궁합 | API 난이도 | 비용 | App Review | 상태 |
|---|---|---|---|---|---|---|
| — | **Threads** | 텍스트 ✅ | — | 무료 | 진행중(Advanced) | ✅ 완료 |
| 1 | **Bluesky** | 텍스트 ✅ | 매우 쉬움 | 무료 | 없음 | ✅ **완료(연결·텍스트발행)** — 이미지·인사이트는 증분2 |
| 2 | **LinkedIn** | 텍스트 ✅ | 중 | 무료 | 있음(가벼움) | ⬜ 예정 |
| 3 | **X (Twitter)** | 텍스트 ✅ | 중 | **유료($100+/월)** | 있음 | ⬜ 보류(비용 결정) |
| 4 | **Instagram + Facebook** | 이미지 ❌(선행필요) | 높음 | 무료 | 무거움 | ⬜ 미디어 생성 후 |
| — | Mastodon | 텍스트 ✅ | 쉬움 | 무료 | 없음 | 선택(니치) |
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

## Phase 2 — LinkedIn (B2B 고가치)

- OAuth2 · 권한 `w_member_social`(+ `openid profile`) · **무료 API**
- 발행: UGC Posts / `rest/posts` (텍스트 + 링크 + 이미지)
- 개인 프로필 우선, 조직 페이지는 후속. App Review 가벼움

## Phase 3 — X (Twitter) (최대 도달, 비용 결정 필요)

- OAuth2 PKCE · API v2 `POST /2/tweets`
- **유료**: Basic $100+/월(쓰기 한도 제한). 무료 티어는 쓰기 극소량 → 사업성 판단 후
- 텍스트 완벽 적합. 비용 승인이 게이트

## Phase 4 — Instagram + Facebook (이미지, 미디어 생성 선행)

- **선행: AI 이미지/영상 생성 기능** (인스타 피드는 텍스트 전용 발행 불가)
- IG: Facebook 로그인 + IG 비즈니스 계정 + `instagram_content_publish` + FB 페이지
- 컨테이너 생성(이미지/영상 URL 필수) → publish. App Review 무거움
- FB 페이지: `pages_manage_posts` (텍스트+링크 가능)
- ⚠️ Threads API엔 `share_to_instagram` 같은 교차게시 파라미터가 **없음**(별도 IG Graph API 통합 필요) — 확인 완료

---

## 권장 실행 묶음

1. **Phase 0 + Phase 1(Bluesky)** 를 한 묶음으로 — 뼈대 + 첫 확장 동시 증명
2. **Phase 2(LinkedIn)** — 무료 텍스트 플랫폼 하나 더로 멀티채널 가치 완성
3. 이후 **X(비용 결정)**, **Instagram/FB(미디어 생성 후)**

## 열린 결정

- [ ] X API 유료 비용($100+/월) 감수 여부
- [ ] Instagram용 AI 이미지 생성 기능 도입 시점
- [ ] Mastodon 포함 여부(쉬우나 니치)
