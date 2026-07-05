# Threads(Meta) 앱 검수 제출 가이드 — PostFlow

Threads API를 **모든 사용자에게** 쓰려면 각 권한을 **Advanced Access**로 올려야 하고, 그건 App Review 승인이 필요하다.
이 문서는 **개발자 콘솔에 그대로 입력할 값 + 권한별 Use Case 문구 + 스크린캐스트 시나리오 + 제출 체크리스트**를 담는다.

> 현황: OAuth·deauthorize·data-deletion 콜백 구현·배포 완료. haru 계정 연결·발행·댓글 조회 실동작 확인.
> 남은 건 **Meta 콘솔 설정 + 권한별 검수 제출**(사람 작업).

---

## 0. Standard vs Advanced Access (핵심 개념)

| 상태 | 의미 |
|---|---|
| **Standard Access** ("테스트 준비 완료") | 앱에 권한 추가만 하면 됨. **앱 역할(admin/tester) 계정 본인 데이터**엔 검수 없이 동작. |
| **Advanced Access** (검수 승인 후) | **모든 사용자 + 타인 공개 데이터**까지. App Review 통과 필요. |

- 지금 haru 계정으로 되는 것(발행·인사이트·댓글·멘션·삭제) = Standard로 충분.
- **트렌드 생성(keyword_search)·경쟁사 분석(profile_discovery)의 "타인 공개 데이터"** = **Advanced 필수**. 승인 전엔 "내 글만" 검색됨.
- **정식 출시(일반 사용자)** = 사용하는 모든 권한을 Advanced로 올려야 함.

---

## 1. 사전 요건 (검수 전 반드시)

| 항목 | 설명 |
|---|---|
| **Meta 개발자 계정 + 앱** | developers.facebook.com → Use case = "Access the Threads API". |
| **Tech Provider 인증** | content_publish 등 고급 권한 전제. 신원 인증, 약 1주. |
| **비즈니스 인증(Business Verification)** | **`threads_keyword_search`·`threads_profile_discovery`는 사실상 필수**(타인 공개 데이터 접근). 사업자등록·D-U-N-S 등으로 조직 인증. 시간이 오래 걸리니 **가장 먼저 착수**. |
| **테스터 계정** | 앱 역할에 Threads Tester 등록(수락 필요) → 스크린캐스트 촬영용. |

**중요**: 검수 전에도 테스터 계정 본인 데이터는 동작 → 스크린캐스트는 테스터 실계정으로 촬영.

---

## ★ 콘솔 복붙 시트 (2026-07-05 실측 확정값)

> 아래 값을 developers.facebook.com 앱 대시보드에 그대로 입력. `🧑` = 형이 직접 채워야 하는 개인/조직 정보.

### App settings → Basic
| 콘솔 필드 | 입력값 |
|---|---|
| Display name | `PostFlow` |
| App domains | `synub.io` |
| Category | `Business` (또는 Productivity) |
| Privacy Policy URL | `https://center.synub.io/ko/policies/privacy` |
| Terms of Service URL | `https://center.synub.io/ko/policies/terms` |
| Website URL | `https://postflow.synub.io` |
| App icon (1024×1024) | ✅ 준비됨 → `apps/web/public/icon-1024.png` 업로드만 |
| Business verification | 🧑 사업자등록/조직 인증 (keyword_search·profile_discovery 전제) |

### Use cases → Access the Threads API → Settings
| 콘솔 필드 | 입력값 |
|---|---|
| Redirect Callback URLs | `https://postflow-api.synub.io/threads/callback` |
| Deauthorize callback URL | `https://postflow-api.synub.io/threads/deauthorize` |
| Data Deletion Request URL | `https://postflow-api.synub.io/threads/data-deletion` |

### 요청 권한 (9종, 체크)
```
threads_basic
threads_content_publish
threads_manage_insights
threads_read_replies
threads_manage_replies
threads_keyword_search
threads_profile_discovery
threads_manage_mentions
threads_delete
```

### App roles → Roles → Testers (Threads Tester로 초대)
`@haru_.developer` · `@deer.rk`

### 🧑 형만 아는 값 (서버 env에만 있음, repo·문서에 넣지 말 것)
- App ID / App Secret (이미 서버 `THREADS_APP_ID`/`THREADS_APP_SECRET`에 설정됨 — 콘솔 값과 동일해야 함)
- 사업자등록번호·D-U-N-S 등 비즈니스 인증 서류

---

## 2. 앱 대시보드 설정값 (그대로 입력)

### OAuth / Redirect (HTTPS 필수, 배포 완료)
| 필드 | 값 |
|---|---|
| **Redirect Callback URLs** | `https://postflow-api.synub.io/threads/callback` |
| **Deauthorize callback URL** | `https://postflow-api.synub.io/threads/deauthorize` |
| **Data Deletion Request URL** | `https://postflow-api.synub.io/threads/data-deletion` |

> ✅ 서버 `THREADS_REDIRECT_URI`가 API 호스트(`postflow-api.synub.io/threads/callback`)로 설정됨 — haru 연결 성공으로 확인됨. Meta 콘솔 Redirect도 동일해야 함.

### 앱 기본 정보
| 필드 | 값 |
|---|---|
| 앱 이름 | PostFlow |
| 카테고리 | Business / Productivity |
| **개인정보 처리방침** | `https://center.synub.io/ko/policies/privacy` |
| **서비스 약관** | `https://center.synub.io/ko/policies/terms` |
| 앱 아이콘 | PostFlow 로고 1024×1024 (`apps/web/public/icon-512.png` 업스케일) |
| 웹사이트 | `https://postflow.synub.io` |

---

## 3. 요청 권한 + Use Case (검수 설명란에 그대로 기재)

코드가 요청하는 scope(application.yml) — **9종**:
`threads_basic, threads_content_publish, threads_manage_insights, threads_read_replies, threads_manage_replies, threads_keyword_search, threads_profile_discovery, threads_manage_mentions, threads_delete`

### 3.1 자동 (검수 불필요)
| 권한 | 용도 |
|---|---|
| **threads_basic** | 계정 연결·프로필·게시물 목록 조회의 기본. |

### 3.2 핵심 — 본인 데이터 (Advanced로 올려 제출)
| 권한 | Use Case 문구 |
|---|---|
| **threads_content_publish** | 사용자가 PostFlow에서 작성/AI 생성한 콘텐츠를 **본인 Threads 계정에 발행·예약 발행**. "발행"/"예약" 클릭 시에만 전송. |
| **threads_manage_insights** | 발행 게시물의 **조회·좋아요·답글·리포스트 인사이트**를 조회해 인사이트/대시보드에 성과 표시. |
| **threads_read_replies** | 사용자 게시물의 **댓글(답글)을 읽어** 앱 내 댓글 뷰어에 표시. |
| **threads_manage_replies** | 사용자가 설정한 규칙에 따라 **본인 게시물 답글에 자동 응답 발행**(댓글 자동화). |
| **threads_manage_mentions** | 사용자를 **멘션한 게시물을 조회**해 멘션 인박스에 표시(응대 지원). |
| **threads_delete** | 사용자가 앱에서 **본인 게시물을 삭제**(내 게시물 목록의 삭제 버튼). |

### 3.3 고급 — 타인 공개 데이터 (**비즈니스 인증 + Advanced 필수**)
| 권한 | Use Case 문구 |
|---|---|
| **threads_keyword_search** | 사용자가 입력한 **키워드로 지금 인기 있는 공개 게시물을 검색**해, AI 콘텐츠 생성에 트렌드(훅·포맷)를 반영("트렌드 반영 생성"). |
| **threads_profile_discovery** | 사용자가 입력한 **공개 계정(@id)의 프로필·최근 성과를 조회**해 경쟁사/벤치마킹 분석 제공. 공개+팔로워 100+ 계정만. |

### 3.4 ℹ️ threads_share_to_instagram — 제출 안 함 (scope에서 제거됨)
- 확인 결과 Threads API엔 인스타 교차게시 파라미터가 **존재하지 않음**(별도 Instagram Graph API 통합 필요).
- 따라서 scope에서 **완전 제거**했고 이번 제출과 무관. 인스타는 `docs/SNS_ROADMAP.md` Phase 4에서 정식 통합.

**공통 데이터 사용 설명**: PostFlow는 콘텐츠 자동화 SaaS. 사용자를 대신해 **본인 계정**에 접근하며, keyword_search/profile_discovery의 공개 데이터는 **사용자에게 트렌드·경쟁 분석을 보여주기 위해서만** 사용. 타인 데이터 저장·판매 없음. 토큰 암호화 저장, 연결 해제/삭제 시 즉시 폐기.

---

## 4. 테스터 계정 등록 (스크린캐스트용)
1. 앱 대시보드 → **App roles → Roles → Testers → Add People**
2. Additional Roles = **Threads Tester**, Threads 사용자명 입력해 초대
3. 초대받은 계정이 **수락**(Threads 앱/알림)
4. 그 계정으로 PostFlow 연결 → 실제 동작 촬영 가능

**초대할 테스터 (현재 연결된 실계정):** `@haru_.developer`, `@deer.rk`

---

## 5. 스크린캐스트 시나리오 (권한별 — 사용자 전체 여정)

Meta는 "엔드포인트 호출"이 아니라 **사용자가 그 기능을 쓰는 전체 흐름**을 요구. 음성/자막 설명 권장. 각 클립은 해당 권한만 깔끔히.

| # | 권한 | 촬영 흐름 |
|---|---|---|
| ① | **content_publish** | 로그인 → AI 생성 → Threads 연결(OAuth 동의) → "발행" 클릭 → Threads 앱에서 게시물 확인 (+예약 보여주면 가점) |
| ② | **manage_insights** | Threads → 인사이트 이동 → 팔로워·조회·좋아요·참여율 표시 → 베스트 게시물/요일별 차트 |
| ③ | **read_replies** | 내 게시물 → 댓글 있는 글의 "댓글 N개" 펼침 → 실제 댓글 목록 표시 |
| ④ | **manage_replies** | 댓글 자동화 → 규칙 생성(키워드→자동답글) → 대상 글에 댓글 달림 → 자동 답글 발행 → Threads에서 확인 |
| ⑤ | **manage_mentions** | 멘션 메뉴 → 나를 언급한 게시물 목록 표시 → "Threads에서 응답" |
| ⑥ | **delete** | 내 게시물 → 삭제 버튼 → 확인 → Threads에서 해당 글 사라짐 확인 |
| ⑦ | **keyword_search** | AI 생성 → "트렌드 반영 생성" 켜고 키워드 입력 → 검색된 실제 인기글이 생성에 반영되는 흐름 |
| ⑧ | **profile_discovery** | 경쟁사 분석 → @공개계정 입력 → 프로필·팔로워·7일 성과 카드 표시 |

> 데모 계정 말고 **테스터 실계정**으로 촬영(데모는 read-only).

---

## 6. 콜백 동작 요약 (검수 문의 대비)

| 콜백 | URL | 동작 |
|---|---|---|
| OAuth | `/threads/callback` | code 교환 → 토큰 저장 → 프론트 복귀 |
| Deauthorize | `/threads/deauthorize` | `signed_request` HMAC-SHA256 검증 → 연결·토큰 서버측 폐기 |
| Data Deletion | `/threads/data-deletion` | 검증 → 삭제 → `{url, confirmation_code}` 반환 |
| 삭제 상태 | `/threads/data-deletion/status?code=` | 사람이 읽는 처리 상태 HTML |

모두 배포됨(`postflow-api.synub.io`). 위조 signed_request는 처리 거부.

---

## 7. 제출 전 체크리스트

- [ ] **비즈니스 인증 착수**(keyword_search·profile_discovery 전제 — 가장 오래 걸림)
- [ ] Tech Provider 인증 완료
- [ ] 개인정보/약관 URL 공개·접속 확인(한국어)
- [ ] Redirect/Deauthorize/DataDeletion 3개 콘솔 입력 + HTTPS 200
- [ ] 테스터 계정 초대·수락 (`@haru_.developer`, `@deer.rk`)
- [ ] 핵심 6권한 스크린캐스트(①~⑥) 업로드
- [ ] 고급 2권한 스크린캐스트(⑦~⑧) + 비즈니스 인증 첨부
- [ ] 각 권한 Use Case 문구 기재(§3)
- [ ] 앱 아이콘·이름·카테고리·웹사이트 입력
- [ ] (승인 후) 앱 **Live 모드** 전환

## 8. 이미 완료된 것 (재작업 불필요)
- OAuth 연결/토큰 저장/60일 리프레시 크론
- 발행·예약·이미지/영상 업로드, 인사이트, 댓글 조회·자동응답, 멘션, 삭제, 트렌드/경쟁사(코드 준비)
- deauthorize·data-deletion **signed_request 검증** 정식 구현
- scope **9종** 요청(share_to_instagram는 제거 — Threads API에 없는 파라미터)

## 참고
- Threads API: https://developers.facebook.com/docs/threads
- Keyword Search(고급 접근·비즈니스 인증): https://developers.facebook.com/docs/threads/keyword-search
- Data Deletion Callback: https://developers.facebook.com/docs/development/create-an-app/app-dashboard/data-deletion-callback/
