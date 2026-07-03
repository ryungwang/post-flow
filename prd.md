# PRD v1.1

> v1.1 변경 요약: 비전·타겟을 일반 사용자까지 확장 / AI 입력 옵션에 일상·캐주얼 추가 / **AI 엔진을 LLMProvider 추상화로 설계(기본 Claude, OpenAI 등 교체 가능)** / **Threads API 실제 제약 반영(네이티브 예약 없음·토큰 60일·지표 한계)** / 인프라를 전역 표준(Lightsail+Caddy+S3)으로 정정 / 온보딩·에러 처리·플랜 매트릭스 신설. 근거는 문서 하단 "Research Notes" 참조.

# Project Name

PostFlow

## Tagline

Create Once. Grow Automatically.

---

# Product Vision

누구나 콘텐츠 작성에 시간을 쓰지 않고 AI를 활용하여 지속적으로 콘텐츠를 생산하고 성장할 수 있는 플랫폼

전문 지식이나 글쓰기 경험이 없어도, 주제만 입력하면 누구나 시작할 수 있다.

---

# Problem

대부분의 사람들은 콘텐츠의 중요성을 알고 있다.

하지만

* 무엇을 써야 할지 모르고
* 꾸준히 쓰기 어렵고
* 성과 분석이 어렵고
* 반복 작업이 귀찮다

결국 콘텐츠 운영을 포기한다.

---

# Solution

주제만 입력하면

AI가

* 아이디어 생성
* 글 작성
* 예약
* 발행
* 분석

까지 수행한다.

---

# Target User

> 핵심: 콘텐츠를 키우고 싶은 **누구나**. 아래는 대표적인 사용 그룹일 뿐, 특정 직군에 한정하지 않는다.

## Primary

* 개발자
* SaaS 운영자
* 창업가
* 마케터

## Secondary

* 강사
* 코치
* 인플루언서
* 컨설턴트

## General

* 콘텐츠를 시작하고 싶지만 무엇을/어떻게 써야 할지 모르는 일반 사용자
* 부업·사이드프로젝트로 SNS를 키우고 싶은 직장인/학생
* 취미·관심사(여행, 음식, 운동 등)를 꾸준히 기록하고 공유하고 싶은 사람
* 자영업자·소상공인 등 홍보가 필요하지만 마케팅 전문성이 없는 사람

---

# Success Metrics

## User

* 첫 게시물 생성까지 3분 이하
* 첫 예약까지 5분 이하

## Business

* 첫 결제 사용자 확보
* 월 구독 전환율 5% 이상

---

# MVP Scope

## Included

* synub 통합계정(SSO) 로그인
* Threads 계정 연결
* AI 콘텐츠 생성
* 콘텐츠 편집
* 예약 발행
* 자동 발행
* 기본 분석

## Excluded

* 댓글 자동 작성
* DM 자동화
* X 지원
* LinkedIn 지원
* 팀 기능

---

# User Flow

회원가입

↓

Threads 연결

↓

주제 입력

↓

AI 생성

↓

예약

↓

자동 발행

↓

성과 확인

---

# Onboarding

> "첫 게시물 3분 이하" 목표의 핵심. 특히 일반 사용자가 막히지 않도록 설계.

1. synub 통합계정(SSO) 로그인
2. Threads 연결 (Meta OAuth) — **연결 전에도 생성은 체험 가능**(발행만 연결 필요)하게 하여 이탈 방지
3. 관심 주제 선택 (추천 칩 + 자유 입력) → 톤·목표는 합리적 기본값 자동 선택
4. **AI가 즉시 3~5개 샘플 생성** → 첫 성공 경험
5. 마음에 드는 카드 → 바로 예약 또는 즉시 발행

* 빈 상태(Empty State)마다 다음 행동 1개를 명확히 제시.
* 토큰 만료/미연결 등은 배너로 안내하고 재연결 동선 제공.

---

# Features

## Authentication

### Login

Google OAuth

### User Profile

* 이름
* 이메일
* 프로필 이미지

---

# Threads Integration

## Account Connect

Meta OAuth

### Status

* Connected
* Expired
* Reconnect Required

### Stored Data

* accessToken (long-lived, **60일** 유효)
* expiresAt
* lastRefreshedAt

> ⚠️ Threads API에는 **refresh_token 개념이 없다.** long-lived 토큰 자체를 갱신한다.
> - 단기 토큰(1시간) → `th_exchange_token`으로 long-lived(60일) 교환
> - long-lived 토큰은 **발급 24시간 후 ~ 만료 전** 사이에 `th_refresh_token`으로 갱신 → 다시 60일 연장
> - **60일 내 미갱신 시 영구 만료** → 사용자 재연결 필요
> - 따라서 스케줄러 cron에 **토큰 사전 갱신 잡**을 반드시 포함 (예: 만료 7일 전 자동 refresh). 누락 시 자동 발행이 조용히 실패한다.

### Required OAuth Scopes

* `threads_basic` (필수, 모든 엔드포인트)
* `threads_content_publish` (발행)
* `threads_manage_insights` (분석)

> 일반 사용자 대상 운영(테스터 외)에는 Meta **App Review(Advanced Access)**가 `threads_content_publish` / `threads_manage_insights` 각각에 필요. 비즈니스 인증 포함 검토 기간을 출시 일정에 반영할 것.

---

# Dashboard

## KPI Cards

* Total Posts
* Total Views
* Total Likes
* Total Comments
* Follower Growth

---

## Recent Posts

Columns

* Content
* Status
* Publish Time
* Views
* Likes
* Comments

---

# AI Content Generator

## Input

### Topic

> 자유 입력 + 추천 칩. 비즈니스/일상 양쪽을 균형 있게 제시한다.

Examples (Business)

* AI
* Startup
* Development
* Real Estate
* Productivity

Examples (Lifestyle)

* 여행
* 음식 / 맛집
* 운동 / 건강
* 육아
* 일상 / 취미

### Goal

* Engagement
* Followers
* Leads
* Sales
* Awareness
* Personal Branding
* Fun / 기록 (일반 사용자)

### Tone

* Expert
* Friendly
* Storytelling
* Controversial
* Educational
* Casual (캐주얼)
* Personal / Diary (일상·일기)
* Humor (유머)

### Quantity

* 5
* 10
* 30

---

# AI Generation Flow

Topic

↓

Hook

↓

Body

↓

Insight

↓

Question

↓

CTA

---

# Generated Content Card

Each Card

* Preview
* Edit
* Copy
* Schedule
* Delete

---

# Content Editor

## Functions

* Edit Content
* Add Hashtags
* Edit CTA
* Preview

Buttons

* Save
* Publish Now
* Schedule

---

# Content Series Generator

Generate

7 Days

14 Days

30 Days

Content Plan

Automatically

---

Example

Topic

AI Startup

Output

Day 1

Why Most AI Startups Fail

Day 2

How To Validate An Idea

Day 3

Mistakes Founders Make

...

Day 30

Lessons Learned

---

# Scheduler

## Schedule Types

One Time

Recurring

Daily

Weekly

> ⚠️ **Threads API는 네이티브 예약 발행을 지원하지 않는다.** (`scheduled_publish_time` / draft 없음 — Instagram/Facebook Graph와 다름)
> → 자체 스케줄러(잡 큐 + cron)를 직접 구현하고, 발행 시점에 publish API를 호출해야 한다. (Buffer/Hootsuite 방식)
>
> **핵심 제약:** 생성한 미디어 컨테이너는 **24시간 후 만료**된다. 따라서 먼 미래 예약 글의 컨테이너를 미리 만들어 둘 수 없다.
> → 워크플로우: 발행 24시간 이내 시점에 컨테이너 생성 → status `FINISHED` 폴링(최대 1회/분, ~5분) → `threads_publish` 호출.

---

# Auto Publishing

Scheduler (cron)

↓

Pending Posts (발행 24h 이내 진입)

↓

Create Container (Threads API) → Poll status until FINISHED

↓

Publish (threads_publish)

↓

Store Result (published_at / threads_media_id / 실패 시 error)

## Rate Limits & Failure Handling

* **발행 한도: 사용자당 250건 / 24시간** (rolling). 캐러셀은 2~20장이 1건으로 카운트. `threads_publishing_limit`로 잔여 조회.
* **재시도 정책:** 컨테이너 생성/발행 실패 시 지수 백오프 재시도(예: 1분→5분→15분, 최대 3회). 초과 시 post status = `failed`, 사용자에게 알림.
* **토큰 만료 감지:** 발행 직전 토큰 유효성 체크, 만료 임박 시 자동 refresh. 영구 만료 시 status = `reconnect_required`.
* **미디어 제약:** 텍스트 ≤500자, 이미지 JPEG/PNG ≤8MB, 비디오 ≤300초/1GB. **이미지/비디오는 공개 URL만 허용**(Meta 서버가 다운로드) → S3 공개 URL 또는 presigned 사용.

---

# Analytics

## Metrics

> Threads Insights API 실제 제공 지표 기준. UI 라벨은 친숙하게 쓰되 매핑을 명확히 한다.

| UI 라벨 | API 필드 | 비고 |
|---|---|---|
| Views | `views` | ⚠️ "in development" — 신뢰도 낮음, 보조 지표로 |
| Likes | `likes` | per-post / per-account |
| Comments | `replies` | **`comments` 필드는 없음 → `replies`로 매핑** |
| Reposts | `reposts` | |
| Quotes | `quotes` | |
| Shares | `shares` | ⚠️ media-level만, "in development" |
| Engagement Rate | (계산) | **API 미제공 → 직접 계산**: `(likes+replies+reposts+quotes)/views` |
| Followers | `followers_count` | account-level, ≥100명부터 demographics 제공 |

> 데이터 한계: 지표 조회 가능 시작일은 **2024-04-13** (그 이전 거부). 핵심 대시보드를 `views`/`shares`에만 의존하지 말 것.

---

## Charts

Daily

Weekly

Monthly

---

## Top Posts

Top 10 Posts

---

# Lead Collection & Content ROI

CTA Templates

Examples

Comment "CHECK"

Get Free Checklist

Join Waitlist

Download Guide

## Content ROI (수익 귀속)

> Threads Insights는 **참여 지표만** 제공(매출 없음). 게시물별 수익은 자체 추적으로 귀속한다.
> 상세 설계: [`docs/CONTENT_ROI.md`](docs/CONTENT_ROI.md).

* 퍼널: **조회(Insights) → 클릭(자체 단축링크 `/r/{slug}`) → 리드 → 전환(매출)**
* 신규 테이블: `cta_links`, `link_clicks`, `leads`, `conversions` (+옵션 `post_cost`)
* 지표: CTR, 리드/구매 전환율, 귀속 매출, 게시물당 매출, RPM, **ROI%**(=（매출−비용)/비용; 비용 없으면 매출·전환율 대표)
* API: `POST /api/posts/{id}/cta-links`, `GET /r/{slug}`(공개·리다이렉트), `POST /api/conversions`, `GET /api/roi/dashboard`
* 단계: **MVP=단축링크 클릭추적 + 매출 수동등록 + ROI 탭** → 호스티드 리드폼·전환 웹훅 → 캠페인/비용
* 프라이버시: 클릭 IP는 해시만, 리드 PII 동의·보존정책, 오픈 리다이렉트 방지

---

# Pricing

## Free

30 Generations / Month

---

## Starter

₩9,900

300 Generations

Scheduling

---

## Pro

₩29,000

Unlimited Generations

Analytics

Series Generator

---

## Business

₩49,000

Multiple Accounts

Priority Support

---

## Plan Feature Matrix

| 기능 | Free | Starter ₩9,900 | Pro ₩29,000 | Business ₩49,000 |
|---|---|---|---|---|
| 월 생성 수 | 30 | 300 | 무제한* | 무제한* |
| 기본 모델 | Sonnet 4.6 | Sonnet 4.6 | Sonnet 4.6 | Sonnet 4.6 |
| 예약/자동 발행 | ❌ | ✅ | ✅ | ✅ |
| 분석 | 기본 | 기본 | 전체 | 전체 |
| 시리즈 생성기(7/14/30일) | ❌ | ❌ | ✅ (Opus 4.8) | ✅ (Opus 4.8) |
| 연결 계정 수 | 1 | 1 | 1 | 다중 |
| 지원 | 커뮤니티 | 이메일 | 이메일 | 우선 |

> *무제한은 공정사용(Fair Use) 상한 적용 — 토큰 비용 폭주 방지. 구체 수치는 베타 사용량 측정 후 확정.
> Free는 발행 없이 "생성 체험"만으로도 가치를 주어 전환을 유도(예약/발행은 Starter+).

---

# AI Engine

## Provider Abstraction (벤더 중립)

> 특정 LLM 벤더에 락인되지 않도록 추상화 레이어를 둔다. 가격·품질 변화에 따라 구현체만 교체.

* `LLMProvider` 인터페이스 정의:
  * `generate(request): Result` — 단건 생성
  * `generateStream(request): Stream` — 스트리밍 생성
  * `generateBatch(requests): List<Result>` — 대량 배치
  * 공통 요청 모델: `prompt`, `systemPrompt`, `outputSchema`, `maxTokens`, `cacheHint`, `modelTier(LIGHT|STANDARD|PREMIUM)`
* 구현체:
  * `ClaudeProvider` (기본, Anthropic Java SDK)
  * `OpenAIProvider` (대안, 동일 인터페이스 — 필요 시 추가)
* `@Profile` / 설정(`ai.provider=claude|openai`)으로 런타임 선택. 기능 코드는 인터페이스에만 의존.
* **modelTier 매핑**으로 벤더별 모델명을 분리(코드는 LIGHT/STANDARD/PREMIUM만 인지):

| Tier | Claude | OpenAI |
|---|---|---|
| LIGHT | Haiku 4.5 | (해당 경량 모델) |
| STANDARD | Sonnet 4.6 | (해당 표준 모델) |
| PREMIUM | Opus 4.8 | (해당 상위 모델) |

* 추상화가 흡수해야 할 벤더 차이: 프롬프트 캐싱 방식, 구조화 출력(JSON schema) 포맷, 토큰 계산/가격, 스트리밍 이벤트 형태, 에러·재시도 코드.
* `ai_generations`에 **provider + model**을 함께 기록(사용량·비용 정산, A/B 비교용).

## Default Model Selection (Claude — 1순위 구현체)

| 용도 | 모델 | 가격 (1M 토큰, in/out) | 이유 |
|---|---|---|---|
| **대량 단건 생성** (기본 워크호스) | `claude-sonnet-4-6` | $3 / $15 | 속도·비용·품질 균형. Free/Starter의 대량 생성에 적합 |
| **프리미엄 품질 / 시리즈 기획** | `claude-opus-4-8` | $5 / $25 | 30일 콘텐츠 플랜 등 장기·일관성 필요 작업. Pro/Business |
| **분류·해시태그 등 경량 작업** | `claude-haiku-4-5` | $1 / $5 | 톤 분류, 해시태그 추출 등 단순 작업 |

> 기본값은 **Sonnet 4.6**으로 단가를 낮추고, Pro/Business 등 상위 플랜의 시리즈 생성에 Opus 4.8을 적용하는 2-티어 전략.

## Prompt Caching

* **브랜드 보이스 / 시스템 프롬프트 / 스타일 가이드**를 캐시 prefix로 고정 → 캐시된 부분 약 **90% 비용 절감**, 지연 감소.
* 캐시는 prefix 매칭 → 시스템 프롬프트는 **고정**하고, 변동값(주제·톤·타임스탬프)은 마지막 breakpoint 뒤에 배치.
* 같은 사용자가 한 세션에 5~30개를 연속 생성하므로 캐시 적중률이 높아 비용 효율 큼.

## Generation Flow (구현)

* Goal/Tone/Topic + 브랜드 보이스 → 구조화 프롬프트(Hook→Body→Insight→Question→CTA).
* **Structured Outputs**(`output_config.format`)로 카드 단위 JSON 강제(본문/해시태그/CTA 필드 분리) → 파싱 안정성.
* 대량(10·30개)은 비용·지연 고려해 **Batch API**(50% 할인) 또는 병렬 호출 + 진행률 스트리밍.
* 500자(Threads 한도) 초과 방지 가드 + 생성 후 검증.

## Cost Guardrails

* 플랜별 월 생성 한도(아래 Plan Matrix)로 토큰 비용 상한.
* `ai_generations`에 prompt/result/모델/토큰수 기록 → 사용량 추적·과금 정산.

---

# Database

## users

id

email

name

profile_image

plan

created_at

---

## social_accounts

id

user_id

provider

access_token

refresh_token

expires_at

---

## posts

id

user_id

content

status (draft / scheduled / publishing / published / failed / reconnect_required)

scheduled_at

published_at

threads_media_id

error_message

retry_count

created_at

---

## analytics

id

post_id

views

likes

comments

shares

engagement_rate

---

## ai_generations

id

user_id

prompt

result

created_at

---

# API

## Auth

POST /api/auth/google

GET /api/auth/me

---

## Threads

POST /api/threads/connect

GET /api/threads/status

---

## AI

POST /api/ai/generate

POST /api/ai/series

---

## Posts

GET /api/posts

POST /api/posts

PUT /api/posts/{id}

DELETE /api/posts/{id}

POST /api/posts/{id}/publish

---

## Analytics

GET /api/analytics/dashboard

GET /api/analytics/post/{id}

---

# Technology Stack

Frontend

React

TypeScript

Vite

Tailwind

Shadcn

React Query

Zustand

---

Backend

Java 21

Spring Boot 3

Spring Security

JPA

QueryDSL

Redis

AI: **LLMProvider 추상화** (기본 구현 = Anthropic Claude, Java SDK `com.anthropic`; OpenAI 등 교체 가능) — "AI Engine" 섹션 참조

---

Database

PostgreSQL

---

Infrastructure (전역 표준 적용 — ECS 아님)

* 호스팅: **AWS Lightsail** (Docker 호스팅)
* 리버스 프록시: **Caddy** (도메인 라우팅 + 자동 HTTPS/Let's Encrypt)
* 파일 스토리지: **AWS S3** (StorageService 추상화 + `@Profile` 분기: local=파일시스템 / prod=S3)
* DB: **PostgreSQL** (Flyway 마이그레이션, schema 분리, `ddl-auto=validate`, UTC)
* 캐시/잡 큐: **Redis** (스케줄러 백오프·발행 큐)

> `docker-compose.yml`과 `Caddyfile`은 **서버(EC2/Lightsail)에서 관리, repo 미포함.** 앱 repo는 Dockerfile + 환경변수 계약만 둔다. 앱 컨테이너는 내부 8080 expose(HTTPS는 Caddy 담당).
> S3 env 규약: `AWS_S3_BUCKET` / `AWS_REGION` / `AWS_S3_PREFIX` / `AWS_S3_PUBLIC_BASE_URL`(공개 에셋 전용 — 발행용 이미지 URL에 사용). 자격증명은 인스턴스 롤 / 표준 `AWS_*` env.

---

# Release Roadmap

V1

AI Generate

Schedule

Publish

---

V2

Analytics

Performance Tracking

---

V3

Comment Assistant

Lead Capture

---

V4

X Integration

LinkedIn Integration

---

V5

Blog Automation

Newsletter Automation

---

# Final Goal

사용자는 콘텐츠를 작성하지 않는다.

AI가 작성한다.

AI가 예약한다.

AI가 발행한다.

사용자는 성장한다.

---

# Research Notes (v1.1 근거)

## Threads API (developers.facebook.com, 2026-06 기준)

* **인증:** OAuth 2.0. 단기 토큰(1h) → long-lived(60일) 교환. **refresh_token 없음** — long-lived 토큰을 `th_refresh_token`으로 직접 갱신(발급 24h 후~만료 전), 미갱신 시 영구 만료.
* **발행:** 2단계 컨테이너 플로우(create → publish). **네이티브 예약 없음** → 자체 스케줄러 필수. 컨테이너 **24h 만료**. 텍스트 500자, 이미지 ≤8MB, 비디오 ≤300s/1GB. 미디어는 **공개 URL만**.
* **한도:** 발행 250건/24h(사용자당, rolling). 캐러셀 2~20장=1건.
* **분석:** `views`/`shares`는 "in development"(불안정). `comments` 없음 → `replies`. `engagement_rate` 미제공(직접 계산). 조회 시작일 2024-04-13.
* **출시 제약:** 일반 사용자 운영에 Meta App Review(Advanced Access) + 비즈니스 인증 필요.

## AI 엔진

* **LLMProvider 추상화**로 벤더 중립 설계 — 기본 구현은 Claude, OpenAI 등 교체 가능. 코드는 LIGHT/STANDARD/PREMIUM 티어만 인지.
* 기본(Claude) 매핑: 워크호스 **Sonnet 4.6**($3/$15), 프리미엄/시리즈 **Opus 4.8**($5/$25), 경량 **Haiku 4.5**($1/$5).
* **Prompt Caching**으로 브랜드/시스템 프롬프트 캐시 → 캐시분 ~90% 절감. **Batch API** 50% 할인.
* Structured Outputs로 카드 JSON 강제, 500자 가드.

## 인프라

* PRD 초안의 ECS/RDS/CloudFront → **전역 표준 Lightsail + Caddy + S3 + PostgreSQL + Redis**로 정정.
