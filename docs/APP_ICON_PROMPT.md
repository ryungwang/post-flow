# PostFlow — 앱 아이콘 생성 프롬프트

> AI 이미지 생성기(Midjourney·DALL·E·ChatGPT·Ideogram 등)에 넣어 앱 아이콘을 만들기 위한 프롬프트.
> PostFlow의 핵심 기능·가치를 반영한다.

## PostFlow가 하는 일 (아이콘이 담아야 할 것)
- **한 번 작성 → 여러 SNS(Threads·Instagram·X·LinkedIn·Bluesky)로 한꺼번에 발행**
- AI가 콘텐츠를 대신 써주고, 예약 발행·댓글 자동화까지 → **소셜미디어 운영을 "수월하게(effortless)"**
- 키워드: **flow(흐름·수월함) · one-to-many(한 번에 여러 채널) · 자동화 · 친근함**

### 기능 → 아이콘 시각 언어 매핑 (모델이 이해하게 프롬프트에 녹임)
| 서비스 기능 | 아이콘이 은유할 것 |
|---|---|
| AI 콘텐츠 생성 | "이미 완성된 하나의 포스트"(손 안 대도 써짐) — 매끈한 콘텐츠 덩어리 |
| 멀티 SNS 동시 발행 | 하나 → **여러 채널 버블로 갈라져 퍼짐**(one-to-many) |
| 수월함/자동화 | **부드러운 곡선의 흐름(flow)**, 힘 안 드는 우아한 움직임 |
| 예약·자동응답 | 알아서 굴러가는 느낌 — 차분하고 자신감 있는 톤 |

> ⚠️ 기능이 많다고 다 그리면 잡다해짐. **"한 포스트가 여러 소셜로 수월하게 흘러간다"는 단 하나의 은유**로 압축하고, 나머지 기능은 프롬프트 설명(context)으로만 준다.

---

## ⭐ 메인 프롬프트 (영어 권장, 복붙)

```
App icon for "PostFlow" — an app that makes running social media effortless.
The user writes ONE post (AI helps write it) and PostFlow flows it out to ALL their
social channels at once, then handles scheduling, comment auto-replies and analytics
for them. The whole feeling is: social media, finally easy and automatic.

Core visual idea (ONE metaphor only): a single finished post flowing smoothly and
gracefully outward, fanning into several soft social-media channel bubbles — a calm,
effortless one-to-many flow. It should feel light and automatic, NOT a busy diagram
or a technical network chart. The graceful curve itself communicates "effortless".

Style: modern 2026 mobile app icon, iOS rounded-squircle, soft 3D "gummy" look with
glossy highlights and a subtle liquid-glass sheen, gentle depth and soft shadows.
Friendly, warm and approachable — rounded soft shapes.

Color: vibrant diagonal gradient from violet #8E63FF through magenta #B84BE8 to warm
pink #FF6AA0; the main symbol in clean glossy white / soft lilac.

Composition: one clear centered symbol, high contrast, instantly readable even at a
tiny 48px size. Minimal, premium, uncluttered.

Do NOT use: sparkles or stars (too generic/AI-cliché), any text or letters, the
Threads logo, camera icons, realistic photography, busy details, drop-shadowed text.

Soft-3D vector look. 1024x1024, centered.
```

## 짧은 버전 (Midjourney / Ideogram)

```
modern app icon "PostFlow", one written post flowing smoothly and fanning into
several social-media bubbles, effortless & friendly, soft 3D gummy + liquid glass,
glossy, rounded squircle, violet-to-pink gradient (#8E63FF #B84BE8 #FF6AA0), clean
white glossy symbol, single centered minimal mark, high contrast, no sparkles, no
text, no stars --ar 1:1 --v 6
```

## 컨셉 변형 (원하는 느낌으로 골라 교체)
- **플로우형**: `a smooth flowing ribbon of content sweeping up and fanning into 3 soft channel bubbles`
- **원탭형**: `one friendly round send button dispatching content out to several social bubbles in an easy arc`
- **허브형**: `several social bubbles gathered comfortably around one rounded home — manage all in one place`

---

## 네거티브 / 피할 것
```
sparkles, stars, glitter, twinkle, magic wand, camera, Threads @ logo, text,
letters, numbers, photorealistic, cluttered, harsh shadows, neon outline
```

## 브랜드 스펙 (고정)
- 그라디언트: `#8E63FF` (violet) → `#B84BE8` (magenta) → `#FF6AA0` (pink), 대각선
- 심볼: 글로시 화이트 / 연보라
- 형태: iOS 스퀴클(rounded square), 소프트 3D + 리퀴드 글래스
- 사이즈: 1024×1024, 여백 여유

## 고를 때 팁
- **48px로 축소**해도 한눈에 알아보이는 것 우선 (앱 목록·파비콘에서 그 크기)
- ✨ 스파클 들어간 건 AI-뻔하니 제외
- 우리 보라→핑크 그라디언트 유지해야 앱 UI와 일관
- 마음에 드는 결과 → 나에게 주면 **1024 정리 + 파비콘·PWA·헤더 로고까지 앱 전체 교체**

---

_참고: 우리가 만들어 본 방향들 — 공유 네트워크(A), 3D 오브, 리퀴드 글래스, 말풍선 마스코트,
콘텐츠 플로우/원탭/스마일/올인원(E1~E4). "편함·flow" 강조가 PostFlow에 가장 맞음._
