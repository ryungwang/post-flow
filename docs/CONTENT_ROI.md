# Content ROI 설계 (콘텐츠 수익 귀속/ROI)

## 1. 문제 정의

Threads API는 **참여 지표(조회·좋아요·댓글·공유)만** 제공하고 매출/수익 데이터는 주지 않는다.
따라서 "이 게시물이 얼마를 벌었나(수익률)"를 보려면, 게시물 이후의 **다운스트림 가치(클릭→리드→전환→매출)를
직접 추적하고 게시물에 귀속**해야 한다.

핵심 메커니즘: 게시물 CTA에 **추적 가능한 단축 링크**를 넣고, 그 링크를 통해 발생한
클릭/리드/전환을 해당 게시물에 귀속한다.

### Threads Insights API와의 관계 (이미 사용 중)

Insights API는 존재하고, 분석 화면이 이미 사용한다 — 하지만 **참여 지표만** 준다:

- 미디어(게시물): `views, likes, replies, reposts, quotes, shares`
- 계정: 위 + `clicks`, `followers_count`, `follower_demographics`

ROI에 못 쓰는 이유:
- **매출/전환 데이터 없음** (참여 ≠ 수익).
- **`clicks`는 계정 레벨만** — 게시물·CTA별 링크 클릭이 아니라 특정 글에 귀속 불가. 미디어 메트릭엔 clicks 자체가 없음.
- `views`/`shares`는 "in development" → 보조용.

→ **결론: Insights는 퍼널 맨 위(조회수=CTR 분모)에 그대로 활용**하고, Insights가 못 주는
**게시물별 클릭→리드→전환(매출)** 만 자체 추적으로 채워 ROI를 완성한다. 둘은 대체가 아니라 보완.

## 2. 퍼널 & 지표

```
조회(views) → 클릭(click) → 리드(lead) → 전환(conversion=매출)
   Threads        단축링크        폼 제출       결제/수익 이벤트
```

| 지표 | 정의 |
|---|---|
| CTR | clicks / views |
| 리드 전환율 | leads / clicks |
| 구매 전환율 | conversions / leads (또는 / clicks) |
| 귀속 매출(revenue) | Σ conversion.amount |
| 게시물당 매출 | revenue / post |
| 1K 조회당 매출(RPM) | revenue / views × 1000 |
| **ROI%** | (revenue − cost) / cost × 100 |

> **"수익률" 정의**: 게시물/캠페인에 **비용(cost)** 이 입력돼 있으면 `ROI% = (매출−비용)/비용`.
> 비용이 없으면(콘텐츠는 한계비용 ≈ 0) ROI% 대신 **매출·구매 전환율·RPM**을 대표 지표로 노출.
> cost는 게시물 단위 수동 입력(광고/프로모션 지출) 또는 캠페인 단위로 받는다(MVP는 수동, 0 기본).

## 3. 귀속(Attribution) 규칙

- 각 게시물 CTA마다 고유 **단축 링크(slug)** 발급 → 링크는 게시물에 1:1~1:N.
- 클릭/리드/전환은 모두 그 **cta_link → post** 로 귀속(결정적 귀속, last-click 불필요).
- 전환은 리드에 연결되면 lead→post, 직접 전환이면 cta_link→post.
- 윈도우: 클릭 후 N일(기본 30일) 내 전환만 귀속(설정값).

## 4. 데이터 모델 (신규 테이블)

```
cta_links     id, post_id(FK), user_id, slug(unique), destination_url, label,
              created_at, updated_at
link_clicks   id, cta_link_id(FK), post_id, clicked_at, referrer, ua, ip_hash
leads         id, post_id, cta_link_id(FK,null), user_id, email, name(null),
              source, status(NEW/QUALIFIED/CONVERTED/LOST), created_at
conversions   id, user_id, post_id, lead_id(FK,null), cta_link_id(null),
              amount(numeric), currency(default 'KRW'), occurred_at, note, source(MANUAL/WEBHOOK)
post_cost     (옵션) post_id, amount, currency  — ROI 분모(지출)
```

- **PII 최소화**: 클릭은 IP를 해시(`ip_hash`)로만, raw IP 미저장. 리드 email은 동의 기반.
- 인덱스: `cta_links.slug`(unique), `link_clicks.post_id`, `leads.post_id`, `conversions.post_id`.

## 5. 추적 엔드포인트

| 메서드 | 경로 | 인증 | 설명 |
|---|---|---|---|
| GET | `/r/{slug}` | 공개 | 클릭 기록(비동기) 후 destination_url로 **302 리다이렉트** |
| POST | `/api/public/leads/{slug}` | 공개 | 호스티드 폼/외부 폼에서 리드 제출 → leads 기록 |
| POST | `/api/conversions` | 인증 | 수동 매출 등록(금액/게시물/리드) |
| POST | `/api/webhooks/conversions` | 서명검증 | 외부(스토어/결제) 전환 웹훅 → 매출 귀속 |
| POST | `/api/posts/{id}/cta-links` | 인증 | 게시물 CTA 단축 링크 생성(destination) |

- `/r/{slug}` 는 봇 필터링(간단 UA 필터) + 중복 클릭 디바운스(동일 ip_hash 짧은 윈도우) 고려.
- 단축 도메인: 로컬은 `http://localhost:8080/r/...`, prod는 짧은 도메인(예: `pf.link/...`) — Caddy 라우팅.

## 6. ROI 집계 API & 화면

- `GET /api/roi/dashboard` — 사용자 합계: views, clicks, leads, conversions, revenue, CTR, 전환율, RPM, (cost 있으면) ROI%.
- `GET /api/roi/post/{id}` — 게시물 퍼널 상세.
- **화면**: 분석 화면에 **"수익/ROI" 탭** 추가 — 퍼널 시각화(조회→클릭→리드→매출), 게시물별 매출 Top, 전환율/RPM, (cost 입력 시) ROI%.

## 7. 단계별 범위

**MVP (Phase 1) — 가장 작고 검증 가능한 루프**
- `cta_links` 생성(게시물에 추적 링크) + `/r/{slug}` 클릭 추적 + 리다이렉트
- `conversions` **수동 등록**(금액 입력) + `leads` 수동/폼 최소
- `GET /api/roi/dashboard` 집계 + 분석 "수익/ROI" 탭(퍼널·매출·전환율·RPM)
- ROI% 는 cost 입력 시에만 노출, 없으면 매출/전환율 중심

**Phase 2**
- 호스티드 리드 캡처 폼(`/api/public/leads/{slug}`) + CTA 템플릿(체크리스트/대기자명단/가이드)
- 전환 웹훅(`/api/webhooks/conversions`, HMAC) — 스토어/결제 연동 자동 매출
- 봇 필터·중복 디바운스 고도화, 귀속 윈도우 설정

**Phase 3**
- 캠페인/비용 관리(post_cost) + 정식 ROI%, 채널 비교, 코호트

## 8. 프라이버시·규정

- 클릭: raw IP 미저장(해시만), 동의 불필요한 집계 수준.
- 리드 email 등 PII: 수집 동의 고지 + 보존기간 정책, 삭제 요청 처리.
- 단축 링크 redirect는 오픈 리다이렉트 방지(destination 화이트리스트/검증).
