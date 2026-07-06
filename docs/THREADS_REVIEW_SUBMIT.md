# Threads 앱 검수 제출 — 복붙 시트

> Meta 콘솔 **검수 → 앱 검수** 에서 각 권한 폼에 **그대로 붙여넣기**용.
> 1차 제출 = **핵심 6권한 + threads_basic** (비즈니스 인증 불필요).
> 2차 제출(비즈니스 인증 승인 후) = `keyword_search` · `profile_discovery`.
> ⚠️ `threads_share_to_instagram` 은 제출에서 **제거**(안 쓰는 권한 → 반려 사유).

---

## 1. 제출 구성 (1차)

```
✅ threads_content_publish
✅ threads_manage_insights
✅ threads_read_replies
✅ threads_manage_replies
✅ threads_manage_mentions
✅ threads_delete
✅ threads_basic            (자동 필수 포함)
✂️ 제외: keyword_search · profile_discovery · share_to_instagram
```

각 권한 폼 = **① 설명 붙여넣기 + ② 스크린캐스트 업로드 + ③ API 테스트(대부분 자동 완료) + ④ 동의 체크 → 저장**.
6개 다 저장 후 → **검토 제출**.

---

## 2. 권한별 상세 설명 (설명란에 그대로 붙여넣기)

### threads_content_publish
```
    사용자가 PostFlow에서 직접 작성하거나 AI로 생성한 콘텐츠를 '본인 Threads 계정'에 발행하는 데 사용됩니다. 사용자가 '발행' 또는 '예약 발행' 버튼을 누를 때만 전송되며, 텍스트·해시태그·CTA·이미지가 함께 게시됩니다. 이 기능으로 사용자는 여러 콘텐츠를 한 곳에서 작성하고 최적 시간에 예약 발행할 수 있습니다.
```

### threads_manage_insights
```
PostFlow는 크리에이터가 자신의 Threads 게시물 성과를 한 곳에서 확인하도록 돕는 콘텐츠 자동화 서비스입니다. 이 권한은 사용자가 발행한 '본인 게시물'의 조회수·좋아요·답글·리포스트·참여율 등 인사이트를 조회하는 데 사용됩니다. 사용자는 앱의 '인사이트' 화면에서 팔로워 추이, 베스트 게시물, 요일별 평균 참여율을 시각적으로 확인하고 다음 콘텐츠 전략을 세울 수 있습니다. 본인 계정 데이터만 조회하며 타인에게 공유·판매하지 않습니다.
```

### threads_read_replies
```
사용자 '본인 게시물'에 달린 댓글(답글)을 읽어 앱 내 '댓글 뷰어'에 표시하는 데 사용됩니다. 사용자는 앱을 벗어나지 않고 자신의 게시물에 달린 반응을 한눈에 확인하고 응대 기회를 놓치지 않을 수 있습니다.
```

### threads_manage_replies
```
사용자가 설정한 규칙(키워드 조건)에 따라 '본인 게시물'의 답글에 자동으로 응답을 발행하는 '댓글 자동화' 기능에 사용됩니다. 예: 특정 키워드가 포함된 댓글에 안내 답글을 자동으로 남깁니다. 사용자가 만든 규칙에 한해서만 동작합니다.
```

### threads_manage_mentions
```
사용자를 멘션(@언급)한 게시물을 조회해 앱의 '멘션' 인박스에 표시하는 데 사용됩니다. 사용자는 자신을 언급한 대화를 한 곳에서 확인하고 빠르게 응대할 수 있습니다.
```

### threads_delete
```
사용자가 앱에서 '본인 게시물'을 삭제하는 데 사용됩니다('내 게시물' 목록의 삭제 버튼). 확인 후 실제 Threads 계정에서 해당 게시물이 삭제됩니다. 본인 게시물에만 적용됩니다.
```

### threads_basic (필요 시)
```
**사용자의 Threads 계정 연결과 프로필·게시물 목록 조회의 기본 권한입니다. 위 모든 기능의 전제로 사용됩니다.**
```

---

## 3. 리뷰어 지침 (권한 폼의 "검수자 안내" 칸 — 공통 붙여넣기)

```
1. https://postflow.synub.io 접속 → "데모로 둘러보기" 버튼으로 전체 UI를 읽기전용으로 체험할 수 있습니다.
2. 실제 발행/삭제/자동응답 등은 첨부한 스크린캐스트를 참고해 주세요. 테스터 계정(@haru_.developer)으로 Threads 연결(OAuth 동의) 후 각 기능을 수행하고 Threads 앱에서 결과를 확인하는 전체 흐름을 담았습니다.
3. 로그인은 Synub 통합계정(SSO)입니다. 필요 시 테스터 계정 자격증명을 제공하겠습니다.
```

---

## 3.5 웹용 테스트 지침 (App Review "웹용 테스트 지침" 모달 — 필드별 복붙)

> Facebook 로그인 미사용 + "데모로 둘러보기" 버튼 + 웹앱 특성에 맞춘 답. 검수자는 SSO 계정이 없으니 **데모 버튼** 안내가 핵심.

**① 어디에서 앱을 찾을 수 있나요? (URL, 필수)**
```
https://postflow.synub.io
```

**② 앱 액세스·테스트 방법 (필수)**
```
PostFlow는 Facebook 로그인을 사용하지 않습니다. 사용자의 Threads 계정을 Threads API(OAuth)로 연결하며, 앱 로그인은 Synub 통합계정(SSO)입니다.

검토 방법:
1) https://postflow.synub.io 접속 → "데모로 둘러보기" 버튼을 누르면 로그인 없이 전체 UI를 읽기전용으로 바로 체험할 수 있습니다. (검수자는 이 데모로 확인해 주세요.)
2) 실제 발행/삭제/댓글 자동응답/멘션 등 쓰기 기능은 첨부한 스크린캐스트를 참고해 주세요. 테스터 계정(@haru_.developer)으로 Threads 연결(OAuth 동의) 후 각 기능을 수행하고 Threads 앱에서 결과를 확인하는 전체 흐름을 담았습니다.
3) 요청 권한은 모두 사용자 '본인 계정' 데이터에만 접근합니다(발행·인사이트·댓글·멘션·삭제).

참고: 현재 정식 오픈 전 비공개 베타라 일반 계정 가입은 제한되지만, "데모로 둘러보기"는 누구나 로그인 없이 접근 가능합니다. 필요 시 테스터 계정 자격증명을 제공하겠습니다.
```

**③ 결제/멤버십 필요 시 액세스 코드·테스트 로그인** → ⚠️ **테스트 로그인 제공** (비번은 Meta 폼에 직접 입력, repo 금지)
```
테스트 로그인 (전체 기능 검토용):
- URL: https://postflow.synub.io
- 아이디: haru
- 비밀번호: (여기에 실제 베타 비밀번호 입력)

이 계정에는 Threads 테스터 계정(@haru_.developer)이 연결돼 있어 발행·인사이트·댓글·댓글 자동응답·멘션·삭제 등 모든 기능을 직접 검토할 수 있습니다. 로그인 후 좌측 메뉴에서 각 기능에 접근하세요. 결제 없이 전체 기능 이용 가능합니다.
```

**④ 결제 필요 시 8~10개 기프트 코드**
```
PostFlow는 웹 애플리케이션으로 앱스토어 다운로드나 인앱 결제가 없습니다. 브라우저에서 무료로 접근 가능하여 기프트 코드는 해당되지 않습니다.
```

**⑤ 지역 제한/지오블로킹 우회 지침**
```
지역 제한이나 지오블로킹이 없습니다. 전 세계 어디서나 https://postflow.synub.io 로 접근할 수 있습니다.
```

---

## 3.6 데이터 처리 설문 (Data Handling) 답

| 질문 | 답 |
|---|---|
| processor-0 (처리자 있나?) | **예** |
| 처리자 이름 | `Amazon Web Services, Inc.` (+ 선택: `Synub Inc.`) |
| 서비스 카테고리 | **IT 솔루션 및 서비스(클라우드 스토리지 및 처리 포함)** |
| 처리 국가 | AWS 리전 국가(서울=**대한민국**) |
| responsible-1 (데이터 관리자) | `Synub Inc.` |
| requests-3 (정부에 데이터 제공?) | **아니요** |
| requests-4 (정부요청 정책) | 합법성 검토 · 이의 제기 · 데이터 최소화 · 요청 문서화 (4개 체크) |

---

## 3.7 필수 API 테스트 호출 — 그래프 API 탐색기 수동 (검수 → 테스트)

> "필수 API 테스트 호출 0/1" 초록불을 켜는 법. **각 권한의 API를 1번씩 실제 호출**하면 됨.
> ⚠️ **Facebook 탐색기 아님** — 반드시 호스트를 **`graph.threads.net`**, 버전 **`v1.0`** 으로.
> ⏱️ 호출은 즉시 등록되지만 **초록불 표시는 최대 24시간** 지연(Meta 사양). 24h 뒤 확인.

### 세팅
1. 검수 → 테스트 → **"그래프 API 탐색기 열기"**
2. 호스트 드롭다운 `.facebook.com/` → **`.threads.net/`**, 버전 **`v1.0`**, Meta 앱 = **PostFlow**
3. **"Generate Threads Access Token"** → **@haru_.developer**(테스터) 동의 → 토큰 채워짐

### 요청 (방식 바꾸고 → 경로 입력 → "제출")
| # | 권한 | 방식 | 경로 |
|---|---|---|---|
| ① | content_publish | POST | `me/threads?media_type=TEXT&text=API test` → 응답 `id` 복사 |
| ①b | (이어서 발행) | POST | `me/threads_publish?creation_id=17862300402642354` → 응답 `id` = **media_id** ⭐ |
| ② | read_replies | GET | `18148114432511371/replies` |
| ③ | manage_replies | POST | `18148114432511371/manage_reply?hide=true` → 이어서 `?hide=false`(원복) |
| ④ | manage_mentions | GET | `me/mentions` |
| ⑤ | delete | DELETE | `18148114432511371` (①에서 만든 테스트글 정리) |

- ③은 **본인 답글은 hide 불가** → 남이 내 글에 단 답글 id를 써야 함(`GET <media_id>/replies` 로 찾음).
- 각 요청이 **에러(빨간 글씨) 없이 JSON 응답**이면 성공 = 등록됨.
- **manage_insights·basic은 앱 실사용만으로 이미 완료**됨(별도 호출 불필요).
- share_to_instagram·keyword_search·profile_discovery는 **1차 제출 대상 아님** → 0/1이어도 무시.

### CLI로 대체 (토큰만 있으면)
```bash
G=https://graph.threads.net/v1.0; T=<threads_token>
curl -X POST "$G/me/threads" --data-urlencode media_type=TEXT --data-urlencode "text=API test" --data-urlencode access_token=$T   # ① → id
curl -X POST "$G/me/threads_publish" --data-urlencode creation_id=<id> --data-urlencode access_token=$T                          # ①b → media_id
curl "$G/<media_id>/replies?access_token=$T"                                                                                     # ②
curl -X POST "$G/<타인답글_id>/manage_reply" --data-urlencode hide=true  --data-urlencode access_token=$T                        # ③
curl -X POST "$G/<타인답글_id>/manage_reply" --data-urlencode hide=false --data-urlencode access_token=$T                        # ③ 원복
curl "$G/me/mentions?access_token=$T"                                                                                            # ④
curl -X DELETE "$G/<media_id>?access_token=$T"                                                                                   # ⑤ 정리
```

---

## 4. 스크린캐스트 체크리스트

- [ ] **테스터 실계정**(`@haru_.developer`)으로 촬영 (데모 아님 — 데모는 read-only)
- [ ] **OAuth 동의 화면 포함 필수** (Threads 연결 → 권한 동의 누르는 장면)
- [ ] 각 권한 **기능을 끝까지** (예: 발행 → Threads 앱에서 올라온 것 확인)
- [ ] 음성/자막 설명 권장
- [ ] 권한별로 깔끔하게(섞지 말 것). 6개를 한 번에 녹화하고 구간별로 잘라도 됨.

### 권한별 촬영 흐름
| 권한 | 흐름 |
|---|---|
| content_publish | 로그인 → AI 생성 → Threads 연결(OAuth 동의) → "발행" → Threads 앱에서 확인 (+예약 보여주면 가점) |
| manage_insights | 인사이트 → 팔로워·조회·좋아요·참여율 + 베스트/요일 차트 |
| read_replies | 내 게시물 → "댓글 N개" 펼침 → 실제 댓글 표시 |
| manage_replies | 댓글 자동화 → 규칙 생성 → 대상 글에 자동 답글 발행 → Threads에서 확인 |
| manage_mentions | 멘션 → 나를 언급한 게시물 목록 |
| delete | 내 게시물 → 삭제 → Threads에서 사라짐 확인 |

---

## 5. 참고
- API 테스트 호출: 이미 haru 계정으로 각 기능을 써봐서 대부분 "완료됨"으로 표시됨. 안 된 게 있으면 **Testing** 탭에서 1회 호출(반영에 최대 24h).
- 앱 아이콘·개인정보·약관 URL·콜백은 이미 입력 완료.
- 고급 2권한(keyword_search·profile_discovery) 상세 설명 = `THREADS_APP_REVIEW.md §3.3`.
