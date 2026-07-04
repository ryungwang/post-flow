# Threads(Meta) 앱 검수 제출 가이드 — PostFlow

Threads API를 정식(모든 사용자) 사용하려면 Meta App Review가 필요하다. 이 문서는 **개발자 콘솔에서 그대로 입력할 값**과 **권한별 스크린캐스트 시나리오**, 제출 전 체크리스트를 담는다.

> 앱은 이미 콜백(OAuth·deauthorize·data-deletion)을 스펙대로 구현·배포했다. 남은 건 **Meta 콘솔 설정 + 검수 제출**(사람 작업).

---

## 0. 사전 요건 (검수 전 반드시)

| 항목 | 설명 |
|---|---|
| **Meta 개발자 계정 + 앱** | developers.facebook.com → 앱 생성 시 **Use case = "Access the Threads API"** 선택. Threads 앱 ID/시크릿은 Facebook 앱과 별개. |
| **Tech Provider 인증** | `threads_content_publish` 등 고급 권한은 **기술 제공자(Tech Provider) 신원 인증** 필요 — 표준 개발자 등록과 별개, 약 1주 소요. |
| **비즈니스 인증**(필요 시) | 조직 계정이면 Business Verification 요구될 수 있음. |

**중요**: 검수 승인 전에도 **본인 계정 + 앱에 등록한 테스터 계정**으로는 발행·테스트 가능. → 스크린캐스트를 이 테스터 계정으로 찍으면 된다.

---

## 1. 앱 대시보드 설정값 (그대로 입력)

### OAuth / Redirect
| 필드 | 값 |
|---|---|
| **Redirect Callback URLs** | `https://postflow-api.synub.io/threads/callback` |
| **Deauthorize callback URL** | `https://postflow-api.synub.io/threads/deauthorize` |
| **Data Deletion Request URL** | `https://postflow-api.synub.io/threads/data-deletion` |

> 3개 모두 **HTTPS 필수**(Meta는 HTTP로 요청 안 보냄). 구현·배포 완료 상태.

### 앱 기본 정보
| 필드 | 값 |
|---|---|
| 앱 이름 | PostFlow |
| 카테고리 | Business / Productivity |
| **개인정보 처리방침 URL** | `https://center.synub.io/ko/policies/privacy` |
| **서비스 약관 URL** | `https://center.synub.io/ko/policies/terms` |
| 앱 아이콘 | PostFlow 로고(1024×1024, `apps/web/public/icon-512.png` 기반 업스케일) |
| 웹사이트 | `https://postflow.synub.io` |

---

## 2. 요청 권한 (Permissions) + Use Case 설명

앱이 실제로 요청하는 스코프(코드 기준): `threads_basic`, `threads_content_publish`, `threads_manage_insights`, `threads_manage_replies`.
`threads_basic`은 기본 제공(검수 불필요). 나머지 3개는 **각각 검수 제출 + 스크린캐스트** 필요(권한당 2~4주).

| 권한 | 앱에서의 용도 (검수 설명란에 기재) |
|---|---|
| **threads_content_publish** | 사용자가 PostFlow에서 작성/AI 생성한 콘텐츠를 **본인 Threads 계정에 발행·예약 발행**. 사용자가 명시적으로 "발행" 또는 "예약"을 누를 때만 전송. |
| **threads_manage_insights** | 발행한 게시물의 **조회수·좋아요·답글 등 인사이트를 조회**해 대시보드/분석 화면에 성과를 표시. |
| **threads_manage_replies** | 사용자가 설정한 규칙에 따라 **본인 게시물의 답글(댓글)을 읽고, 자동 답글을 발행**(댓글 자동화 기능). 사용자가 규칙을 켠 계정에서만 동작. |

**공통 데이터 사용 설명**: PostFlow는 콘텐츠 자동화 SaaS로, 사용자를 대신해 사용자 **본인 Threads 계정**에만 접근한다. 타인 데이터 수집·판매 없음. 액세스 토큰은 암호화 저장, 연결 해제/삭제 요청 시 즉시 폐기.

---

## 3. 테스터 계정 등록 (스크린캐스트 촬영용)

1. 앱 대시보드 → **App roles → Roles → Testers 탭 → Add People**
2. Additional Roles = **Threads Tester** 선택, Threads 사용자명 입력해 초대
3. 초대받은 계정이 **수락**해야 활성 (Threads 앱/알림에서 수락)
4. 이후 그 계정으로 PostFlow에서 연결 → 발행/분석/자동답글 **실제 동작 촬영 가능**

권장 테스터: haru 본인 Threads 계정 + 팀원 1개.

---

## 4. 스크린캐스트 시나리오 (권한별 — 전체 사용자 여정을 보여줄 것)

Meta는 "엔드포인트를 호출한다"가 아니라 **사용자가 그 기능을 쓰는 전체 흐름**을 요구한다. 각 권한마다 아래 흐름을 화면 녹화(음성/자막 설명 권장).

### ① threads_content_publish
1. PostFlow 로그인 → 대시보드
2. **AI 생성**에서 키워드 입력 → 콘텐츠 생성
3. **Threads 계정 연결**(설정 → Threads 연결 → OAuth 동의화면 → 복귀)
4. 생성 콘텐츠를 **"발행"** 클릭 → 성공 → 실제 Threads 앱에서 그 게시물 확인
5. (예약도 보여주면 가점) 스케줄에 예약 → 예약 목록 노출

### ② threads_manage_insights
1. 이미 발행된 게시물이 있는 상태
2. **분석/대시보드** 이동 → 조회수·좋아요·참여율 지표가 표시되는 화면
3. 특정 게시물 상세의 인사이트 확인

### ③ threads_manage_replies
1. **댓글 자동화** 메뉴 → 규칙 생성(예: 특정 키워드 답글에 자동 응답)
2. 규칙 저장 → 대상 게시물에 실제 답글이 달림 → PostFlow가 **자동 답글 발행**
3. 결과를 Threads 앱에서 확인

> 촬영 팁: 데모 계정 말고 **테스터 실계정**으로 찍을 것(데모는 read-only라 발행 불가). 각 클립은 해당 권한 흐름만 깔끔히.

---

## 5. 콜백 동작 요약 (검수 문의 대비)

| 콜백 | URL | 동작 |
|---|---|---|
| OAuth 콜백 | `/threads/callback` | code 교환 → 토큰 저장 → 프론트 복귀 |
| Deauthorize | `/threads/deauthorize` | `signed_request` HMAC-SHA256 검증 → 해당 Threads 계정 연결·토큰 서버측 폐기 |
| Data Deletion | `/threads/data-deletion` | 검증 → 데이터 삭제 → `{url, confirmation_code}` 반환 |
| 삭제 상태 페이지 | `/threads/data-deletion/status?code=` | 사람이 읽는 처리 상태 HTML |

모두 배포됨(`postflow-api.synub.io`). signed_request는 앱시크릿으로 검증하며, 위조 요청은 데이터 삭제하지 않는다.

---

## 6. 제출 전 체크리스트

- [ ] Tech Provider 인증 완료(고급 권한 전제)
- [ ] 개인정보/약관 URL 접속 확인(공개, 한국어)
- [ ] Redirect/Deauthorize/DataDeletion URL 3개 콘솔 입력 + HTTPS 200 확인
- [ ] 테스터 계정 초대·수락 완료
- [ ] 권한 3개 각각 스크린캐스트 업로드(전체 여정)
- [ ] 각 권한 Use Case 설명 기재(§2 문구)
- [ ] 앱 아이콘·이름·카테고리·웹사이트 입력
- [ ] 앱을 **Live 모드**로 전환(검수 후)

## 7. 앱이 이미 완료한 것 (재작업 불필요)
- OAuth 연결/토큰 저장/갱신(60일 만료 리프레시 크론)
- 발행·예약·이미지/영상 업로드, 인사이트 수집, 댓글 자동답글
- deauthorize·data-deletion 콜백 **signed_request 검증** 정식 구현
- scope에 `threads_manage_replies` 포함(댓글 자동화 필수)

## 참고 문서
- Threads API 게시: https://developers.facebook.com/docs/threads
- Data Deletion Callback: https://developers.facebook.com/docs/development/create-an-app/app-dashboard/data-deletion-callback/
