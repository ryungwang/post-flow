# PostFlow — 출시 체크리스트

> 공개 출시까지 **누가 · 무엇을 · 어떤 순서로** 해야 하는지 한 눈에.
> 범례: 🧑 형(수동) · 🤖 코드(내가 가능) · 🏛 Meta 심사(외부, 수 주)
> _최종 점검: 2026-07-05 (prod 실측)_

---

## 0. 오늘 실측한 출시 준비 상태 ✅

| 항목 | 상태 |
|---|---|
| 프론트 `postflow.synub.io` | ✅ 200 |
| 백엔드 `postflow-api.synub.io` health | ✅ 200 |
| 랜딩 `/landing` | ✅ 200 |
| 법적 페이지 privacy·terms (center.synub.io) | ✅ 200 (한국어 공개) |
| Threads 콜백 3종 (callback/deauthorize/data-deletion) | ✅ 정상(파라미터 없이 400, 502 아님) |
| 데모 모드 · 빌링 게이팅 · UX · 다중계정 | ✅ 완료·검증 |

→ **Meta App Review를 제외하면 기술적으로 출시 준비 완료.**

---

## 1. 🏛 크리티컬 패스 — Meta App Review (진짜 관문)

현재 Threads 앱은 **dev 모드** → 테스터로 등록된 계정만 Threads 연결·발행 가능.
**일반 사용자가 쓰려면 App Review 승인 + Live 전환 필수.** 상세 절차: `docs/THREADS_APP_REVIEW.md`

- [ ] 🧑 **비즈니스 인증 착수** (keyword_search·profile_discovery 전제 — 가장 오래 걸림, 먼저 시작)
- [ ] 🧑 Tech Provider 인증 완료
- [ ] 🧑 Redirect/Deauthorize/DataDeletion 3개 콘솔 입력 (URL은 §0에서 200 확인됨)
- [ ] 🧑 테스터 계정 초대·수락
- [ ] 🧑 핵심 6권한 스크린캐스트(①~⑥) + 고급 2권한(⑦~⑧) 업로드
- [ ] 🧑 각 권한 Use Case 문구 기재 (문구는 THREADS_APP_REVIEW.md §3에 완비)
- [ ] 🏛 **심사 통과 대기** (수 주)
- [ ] 🧑 승인 후 앱 **Live 모드** 전환

> ⚠️ **이게 승인돼야 아래 "공개 전환"이 의미 있음.** 승인 전 공개하면 일반 사용자가 Threads 연결 시 dev-mode 에러.

---

## 2. 🤖 공개 전환 스위치 — 베타 허용목록 해제 (승인 후 1줄)

현재 3계정만 로그인 가능:
```
AUTH_ALLOWED_EXTERNAL_IDS=usr_admin_haru,usr_office_sky,usr_office_admin
```
동작: **목록이 비면 전체 허용**(JwtAuthenticationFilter). 공개 시:

- [ ] 🤖/🧑 서버 env에서 `AUTH_ALLOWED_EXTERNAL_IDS`를 **빈 값**으로 → 재시작 → 누구나 로그인
- [ ] 🧑 (선택) 데모 배너/문구를 "베타" → "정식" 톤으로
- [ ] 🧑 가격/플랜 카탈로그 최종본 확정 (synub-billing) — 문구 보강 권장: Pro에 "댓글 자동화·인사이트·경쟁사 분석" 추가

> **타이밍**: App Review 승인 직후에 이 스위치를 켠다. (승인 전엔 유지)

---

## 3. 🤖 런칭 직전 프리플라이트 (D-day 스모크 테스트)

- [ ] 프론트/백엔드/랜딩 200 (오늘 확인 — 배포 후 재확인)
- [ ] 법적 페이지 privacy·terms 200 (오늘 확인)
- [ ] 데모 로그인 → 대시보드/생성/라이브러리 렌더 정상
- [ ] 실계정 로그인 → Threads 연결 → 발행 1건 E2E (승인 후)
- [ ] 결제 플랜 게이팅: FREE 402 차단 / PRO 통과 (오늘 확인)
- [ ] 모바일 반응형 1회 점검
- [ ] OG 링크 미리보기(카톡/슬랙 공유) 정상

---

## 4. 🤖 출시 후 운영 (있으면 좋음)

- [ ] 에러 모니터링(Sentry 등) 연동 — 프론트/백엔드 예외 추적
- [ ] 가입/활성/발행 지표 대시보드
- [ ] 알림(장애·결제실패) 라우팅
- [ ] 백업·복구 절차 확인 (Postgres)

---

## 5. ✅ 이미 완료 (재작업 불필요)

- 배포 파이프라인(AWS Lightsail + Caddy + S3, Vercel SPA) — `docs/DEPLOY.md`
- SSO 로그인(synub-sso RS256) + 빌링 entitlement 게이팅(context-aware)
- Threads 핵심: 발행(해시태그·CTA)·게시물·인사이트·댓글·멘션·삭제·다중계정
- 전 서비스 UX 표준: 커스텀 confirm/토스트 + 회사 로고 로딩 + 게이팅(양방향 검증)
- 데모(체험) 모드 + 비공개 베타 허용목록
- 법적 페이지 · 파비콘/PWA/OG · 브랜딩

---

## 6. 열린 결정 (형)

- [ ] 출시 시점 = App Review 승인일 기준 vs 소프트 론칭(테스터 한정 먼저)
- [ ] X(Twitter) 유료 API 감수 여부 → `docs/SNS_ROADMAP.md`
- [ ] 에러 모니터링 도구 선택(Sentry 무료 티어 권장)

---

## 요약: 출시까지 남은 진짜 일

1. 🏛 **Meta App Review 제출·승인** (형+Meta, 수 주) ← 유일한 실질 관문
2. 🤖 승인 후 **허용목록 해제 1줄** → 공개
3. 🤖 프리플라이트 스모크 → 런칭

나머지(코드·UX·게이팅·법적·배포)는 **다 끝나 있음.**
