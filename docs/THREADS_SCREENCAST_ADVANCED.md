# 고급 2권한 스크린캐스트 대본 (keyword_search · profile_discovery)

> **핵심**: 승인 전이라 실데이터는 안 나옴. 대신 **① OAuth 동의 화면에서 두 권한을 요청하는 장면**(필수) + **② 앱 내 실제 사용 흐름** + **③ 내레이션**으로 구성.
> 테스터 실계정(`@haru_.developer`)으로. 총 2~3분. 하나로 찍고 권한별로 잘라 업로드 가능.
> ⚠️ 이 두 권한은 검수 승인 후 실데이터가 활성화됨 — 검수자 안내에 명시.

## 준비
- 브라우저 탭 A: `https://postflow.synub.io` (haru 로그인)
- 녹화: **⌘⇧5 → 기록**
- 자막(영문 권장) 또는 음성 내레이션

---

## ▶ CLIP A — OAuth 동의 (두 권한 요청 장면 · **필수**)
> 자막: **"Connect Threads — requesting Search & Lookup permissions"**

1. 사이드바 **Threads → 계정 연결**
2. **"다시 연결"** 클릭 → Threads OAuth 동의 화면
3. 목록에서 아래 두 항목이 보이게 **잠깐 정지**:
   - **"Search and reply to public Threads posts"** (= threads_keyword_search)
   - **"Lookup public users and their Threads posts"** (= threads_profile_discovery)
4. (강추) **"액세스 권한 수정"** 열어 두 토글이 **켜져 있는 상태**를 보여주고 승인
5. 승인 → 앱으로 복귀

*이 장면이 두 권한 검수의 핵심 증거예요. 반드시 포함.*

---

## ▶ CLIP B — profile_discovery (경쟁사 분석)
> 자막: **"Competitor analysis — look up a public account (@zuck)"**

1. 사이드바 **Threads → 경쟁사 분석**
2. 안내문 보이게: *"벤치마킹할 계정의 @아이디를 넣으면 팔로워·최근 7일 성과를 보여줘요."*
3. 입력창에 **`zuck`** 입력 → 조회 클릭
4. 응답 표시
   - 내레이션: *"공개 계정의 프로필과 최근 7일 성과(팔로워·조회·좋아요·리포스트)를 조회해 벤치마킹에 활용합니다. 공개 + 팔로워 100명 이상 계정만 대상이며, 승인 후 실데이터가 표시됩니다."*

---

## ▶ CLIP C — keyword_search (트렌드 반영 생성)
> 자막: **"Trend-aware generation — search trending public posts by keyword"**

1. 사이드바 **콘텐츠 → AI 생성**
2. **"트렌드 반영 생성"** 토글 켜기
3. 트렌드 키워드 입력 (예: **`재테크`**) → 안내문 보이게: *"이 키워드로 지금 반응 좋은 Threads 글을 찾아 AI가 그 훅·포맷을 반영해요."*
4. 주제 입력 → **생성** 클릭 → 결과 카드
   - 내레이션: *"사용자가 입력한 키워드로 인기 공개 게시물을 검색해, 어떤 훅·포맷이 반응이 좋은지 트렌드를 파악하고 콘텐츠 생성에 반영합니다. 승인 후 실검색이 활성화됩니다."*

---

## 검수자 안내(권한 폼 "검수자 안내" 칸에 공통 추가)
```
이 두 권한(keyword_search·profile_discovery)의 실데이터는 검수 승인 시 활성화됩니다.
현재 개발 단계라 Threads가 승인 전에는 공개 데이터 응답을 제한하지만, 앱의 사용 흐름과
OAuth 동의(권한 요청)는 영상에서 확인할 수 있습니다. 필수 API 테스트 호출은 완료(초록불)했습니다.
- profile_discovery: 경쟁사 분석 화면에서 공개 계정(@zuck) 조회 → 프로필·최근 7일 성과 벤치마킹
- keyword_search: AI 생성의 '트렌드 반영'에서 키워드로 인기 공개 글을 검색해 훅·포맷 반영
두 권한 모두 공개 데이터를 '사용자에게 트렌드·경쟁 분석을 보여주기 위해서만' 사용하며 저장·판매하지 않습니다.
```

## 촬영 팁
- **OAuth 동의 화면(CLIP A) 필수** — 두 권한 항목이 화면에 보여야 함.
- 실데이터가 안 나와도 OK — 사용 흐름 + 내레이션 + 동의 화면이면 충분.
- 각 동작 후 1~2초 정지. 영문 자막이면 리뷰어 이해 빠름.
- 설명 텍스트: `THREADS_REVIEW_SUBMIT.md`의 고급 2권한 설명 문단 사용.
