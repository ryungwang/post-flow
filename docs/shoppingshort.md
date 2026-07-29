# 쇼핑 쇼츠 자동 생성 시스템 구축 작업지시서

기존 프로젝트와 분리된 로컬 실행용 쇼핑 쇼츠 자동 생성 프로젝트를 구축한다.

이 프로젝트는 쿠팡 상품을 판매하거나 등록하는 시스템이 아니다.

사용자가 직접 선택한 쿠팡 상품을 쿠팡파트너스 쇼츠 광고로 제작하기 위한 내부용 자동화 시스템이다.

최우선 목표는 다음과 같다.

* 적은 입력으로 쇼츠 제작
* 상품별로 여러 광고 기획 생성
* 선택한 광고만 영상 제작
* Claude를 광고 기획 및 감독으로 사용
* Kling을 장면 영상 생성에 사용
* Remotion과 FFmpeg로 최종 영상 합성
* API 호출과 영상 생성 비용 최소화
* 실패한 단계부터 재실행
* 생성된 결과와 중간 산출물 영구 재사용
* 필요할 때 로컬에서 프로젝트를 실행해 사용

---

# 1. 기본 원칙

다음 원칙을 반드시 준수한다.

* 서버에서 쿠팡 사이트를 직접 크롤링하지 않는다.
* 쿠팡 URL에 서버 요청을 보내 상품 정보를 수집하지 않는다.
* Selenium, Playwright, 프록시, 반복 크롤링을 사용하지 않는다.
* 사용자가 직접 열어 놓은 쿠팡 상품 페이지의 DOM만 브라우저 확장 프로그램에서 읽는다.
* 추출된 데이터만 로컬 프로젝트로 전달한다.
* 쿠팡 상품 URL과 쿠팡파트너스 URL은 링크 저장 및 최종 문구 생성 용도로만 사용한다.
* 상품 정보에 존재하지 않는 성능, 효과, 효능을 임의로 생성하지 않는다.
* 가격은 변경될 수 있으므로 추출 시각을 함께 저장한다.
* 상품 이미지와 상세 이미지의 실제 외형을 최대한 유지한다.
* AI 영상에서 상품 로고, 색상, 버튼, 구성품, 형태를 임의로 변경하지 않도록 한다.
* 생성형 AI가 상품 글자나 가격을 직접 만들지 않게 한다.
* 상품명, 가격, CTA, 고지 문구는 Remotion에서 정확한 텍스트로 합성한다.
* 동일 입력과 동일 설정에 대해서는 기존 결과를 우선 재사용한다.
* 전체 재생성보다 부분 재생성을 우선한다.
* 불필요한 Claude 및 Kling API 호출을 금지한다.

---

# 2. 권장 기술 구성

기존 프로젝트의 기술 스택과 충돌하지 않는 범위에서 다음 구성을 우선 사용한다.

## 로컬 애플리케이션

* 백엔드: Spring Boot
* 프론트엔드: React 또는 기존 프로젝트 프론트 구조
* 데이터베이스: SQLite 또는 로컬 PostgreSQL
* 작업 큐: 데이터베이스 기반 Job Queue
* 영상 편집: Remotion
* 영상 후처리: FFmpeg
* 텍스트 AI: Claude API
* 영상 생성: Kling API
* 음성 생성: 교체 가능한 TTS Provider 구조
* 파일 저장: 로컬 파일 시스템
* 브라우저 연동: Chrome Extension

외부 Redis, Kafka, AWS SQS 등은 초기 버전에 사용하지 않는다.

로컬에서 하나의 프로젝트를 실행하면 전체 기능을 사용할 수 있도록 구성한다.

---

# 3. 전체 처리 흐름

전체 프로세스를 다음 순서로 구현한다.

1. 사용자가 쿠팡 상품 페이지를 직접 연다.
2. Chrome Extension 버튼을 누른다.
3. Extension이 현재 페이지 DOM에서 상품 정보를 추출한다.
4. 추출 결과를 로컬 애플리케이션으로 전송한다.
5. 사용자가 쿠팡파트너스 URL을 직접 입력한다.
6. 시스템이 상품 정보와 이미지를 검증한다.
7. Claude가 상품을 한 번 분석한다.
8. Claude가 광고 기획안 3~5개를 생성한다.
9. 사용자가 기획안을 선택한다.
10. 선택된 기획안의 스토리보드를 확정한다.
11. Kling이 필요한 핵심 장면만 생성한다.
12. TTS 음성을 생성한다.
13. Remotion과 FFmpeg로 원본 이미지, AI 영상, 자막, 음성, BGM, CTA를 합성한다.
14. 최종 MP4, 썸네일, 제목, 설명, 해시태그, 고정댓글을 저장한다.
15. 이후 수정 시 필요한 단계만 다시 실행한다.

---

# 4. Chrome Extension 구현

쿠팡 상품 페이지에서 실행되는 Chrome Extension을 구현한다.

Extension은 사용자가 직접 열어 놓은 현재 탭에서만 동작한다.

## 추출 대상

가능한 경우 다음 값을 추출한다.

* 상품명
* 현재 가격
* 원래 가격
* 할인율
* 브랜드
* 카테고리
* 대표 이미지
* 상세 이미지
* 옵션
* 구성품
* 상품 설명
* 상세페이지 주요 문구
* 상품 페이지 URL
* 추출 시각

## 이미지 처리

* 이미지 URL 목록을 추출한다.
* 중복 이미지를 제거한다.
* 작은 아이콘, 배너, 로고성 이미지, 리뷰 프로필 이미지는 제외한다.
* 상품과 직접 관련된 이미지 위주로 정리한다.
* 대표 이미지와 상세 이미지를 구분한다.
* 로컬 애플리케이션에서 이미지 미리보기를 제공한다.
* 사용자가 불필요한 이미지를 제외할 수 있도록 한다.
* 사용자가 직접 이미지를 추가 업로드할 수 있도록 한다.

## Extension 제한

* 자동 페이지 순회 금지
* 여러 상품 자동 수집 금지
* 백그라운드 반복 요청 금지
* 쿠팡 로그인 정보 수집 금지
* 쿠키 및 인증정보 서버 전송 금지
* 사용자가 버튼을 눌렀을 때 현재 페이지 한 건만 처리

DOM 구조가 변경되어 추출에 실패하면 수동 입력 화면으로 전환한다.

---

# 5. 상품 원본 데이터 저장

상품 정보를 최초 한 번만 저장한다.

상품별 고유 ID를 생성한다.

다음 파일 또는 데이터 구조를 유지한다.

```text
workspace/
  products/
    {productId}/
      product.json
      source/
        images/
        screenshots/
      analysis/
      campaigns/
      shared-assets/
```

`product.json`에는 다음 정보를 저장한다.

```json
{
  "productId": "",
  "productName": "",
  "brand": "",
  "category": "",
  "price": null,
  "originalPrice": null,
  "discountRate": null,
  "options": [],
  "features": [],
  "description": "",
  "productUrl": "",
  "affiliateUrl": "",
  "sourceImages": [],
  "extractedAt": "",
  "createdAt": "",
  "updatedAt": ""
}
```

상품 정보와 원본 이미지는 다른 광고 버전에서도 공통으로 재사용한다.

---

# 6. 입력 검증 및 사용자 보완

Claude 호출 전에 상품 정보가 충분한지 검증한다.

필수값은 다음과 같다.

* 상품명
* 대표 이미지 1개 이상
* 상품 특징 또는 상세 설명
* 쿠팡파트너스 URL

부족한 경우 사용자가 직접 보완할 수 있도록 한다.

사용자가 수정할 수 있는 값은 다음과 같다.

* 상품명
* 가격
* 특징
* 타깃 고객
* 강조할 내용
* 제외할 내용
* 이미지 선택
* 파트너스 URL
* 광고 길이
* 광고 스타일
* TTS 목소리
* 생성할 광고 기획 개수

---

# 7. Claude 역할

Claude는 텍스트 작성기가 아니라 다음 역할을 수행한다.

* 상품 분석가
* 퍼포먼스 마케터
* 쇼츠 광고 감독
* 스토리보드 작성자
* Kling 프롬프트 작성자
* 자막 및 나레이션 작성자
* 업로드 문구 작성자

Claude가 영상 파일을 직접 만들지는 않는다.

Claude API 응답은 반드시 정의된 JSON Schema로 받는다.

자유 형식 텍스트를 그대로 저장하거나 파싱하지 않는다.

---

# 8. 상품 분석

상품별 분석은 원칙적으로 최초 1회만 실행한다.

Claude가 다음 항목을 분석한다.

* 상품 카테고리
* 핵심 사용 대상
* 주요 타깃 연령 또는 상황
* 사용 장소
* 사용 시점
* 해결하는 문제
* 핵심 장점
* 구매 포인트
* 구매 망설임 요소
* 강조 가능한 사실
* 강조하면 안 되는 내용
* 영상에서 실제 이미지로 보여줘야 하는 요소
* AI 영상으로 연출해도 되는 요소
* 추천 광고 스타일
* 추천 영상 길이
* 추천 영상 분위기
* 추천 CTA
* 상품 이미지별 활용 용도

분석 결과는 `analysis/product-analysis.json`에 저장한다.

상품 데이터가 변경되지 않았다면 다시 분석하지 않는다.

---

# 9. 광고 기획안 다중 생성

상품 분석 완료 후 영상부터 만들지 않는다.

Claude가 비용이 적게 드는 텍스트 광고 기획안만 먼저 3~5개 생성한다.

기본 광고 스타일 후보는 다음과 같다.

* 문제 해결형
* 리뷰형
* 생활 꿀템형
* 비교형
* 감성형
* 프리미엄 광고형
* 발견형
* 구매 전 확인형
* 사용 상황형
* 가성비형

모든 스타일을 고정 생성하지 않는다.

상품에 가장 적합한 스타일을 Claude가 선택해 서로 겹치지 않는 기획안으로 생성한다.

각 기획안에는 다음을 포함한다.

* 기획안 ID
* 광고 스타일
* 핵심 콘셉트
* 타깃
* 후킹 문구
* 전개 방식
* 핵심 판매 포인트
* CTA
* 예상 영상 길이
* AI 영상 장면 필요 개수
* 원본 이미지 활용 비율
* 제작 비용 등급
* 상품 정확성 위험도
* 추천 점수
* 추천 이유

기획안은 아직 Kling을 호출하지 않는다.

사용자가 하나 또는 여러 개를 선택한 경우에만 영상 제작을 진행한다.

기획안별로 한 개의 최종 쇼츠가 생성되도록 한다.

---

# 10. 비용 절감 모드

기본 모드는 비용 절감 모드로 구현한다.

## 기본 생성 정책

20~30초 영상 기준으로 총 5개 장면을 구성한다.

* AI 영상 장면: 최대 2개
* 원본 이미지 장면: 최소 3개

AI 영상이 반드시 3개 필요하다고 고정하지 않는다.

Claude가 상품 특성과 원본 이미지 품질을 보고 0~2개 사이에서 결정하도록 한다.

원본 이미지로 충분하면 Kling 호출 없이 영상 제작이 가능해야 한다.

## AI 영상이 필요한 장면 예시

* 첫 2~3초 후킹
* 실제 사용 분위기를 보여주는 핵심 장면
* 원본 이미지로 표현하기 어려운 카메라 연출

## AI 영상이 필요하지 않은 장면 예시

* 상품 대표 이미지
* 상세 이미지
* 구성품 설명
* 가격 및 할인 정보
* 기능 목록
* CTA
* 파트너스 고지
* 상품 비교 카드

원본 이미지 장면에는 다음 효과를 적용한다.

* Ken Burns
* Zoom In
* Zoom Out
* Pan
* Parallax
* Depth
* Crop Animation
* Mask Reveal
* Light Sweep
* Blur Transition
* Card Animation
* Feature Highlight

---

# 11. 스토리보드 생성

사용자가 선택한 광고 기획안마다 스토리보드를 생성한다.

기본 길이는 20초로 한다.

선택 가능한 길이는 다음과 같다.

* 15초
* 20초
* 30초

기본 Scene 수는 5개로 한다.

길이에 따라 4~7개 범위에서 조정할 수 있다.

각 Scene에는 다음 정보를 포함한다.

```json
{
  "sceneId": "",
  "order": 1,
  "purpose": "",
  "duration": 3.0,
  "sourceType": "ORIGINAL_IMAGE",
  "sourceAssetIds": [],
  "visualDescription": "",
  "cameraShot": "",
  "cameraMovement": "",
  "lighting": "",
  "mood": "",
  "transitionIn": "",
  "transitionOut": "",
  "caption": "",
  "narration": "",
  "soundEffect": "",
  "klingPrompt": "",
  "negativePrompt": "",
  "requiresAiGeneration": false
}
```

`sourceType`은 다음 값만 사용한다.

* `ORIGINAL_IMAGE`
* `AI_VIDEO`
* `TEXT_CARD`
* `COMPOSITE`

스토리보드 전체 나레이션 길이가 선택한 영상 길이를 초과하지 않도록 검증한다.

---

# 12. Claude 출력 JSON 구조

Claude 결과는 최소한 다음 구조를 만족해야 한다.

```json
{
  "productAnalysis": {},
  "campaignCandidates": [
    {
      "campaignId": "",
      "style": "",
      "concept": "",
      "hook": "",
      "targetAudience": "",
      "sellingPoints": [],
      "cta": "",
      "recommendedDuration": 20,
      "estimatedAiSceneCount": 1,
      "estimatedCostLevel": "LOW",
      "recommendationScore": 0
    }
  ],
  "selectedCampaign": {
    "campaignId": "",
    "title": "",
    "style": "",
    "hook": "",
    "scenes": [],
    "youtube": {
      "title": "",
      "description": "",
      "hashtags": [],
      "pinnedComment": ""
    },
    "disclosure": ""
  }
}
```

JSON Schema 검증 실패 시 Claude에 전체 내용을 다시 요청하지 않는다.

가능하면 잘못된 필드만 수정 요청한다.

---

# 13. Kling 프롬프트 생성 규칙

Kling 프롬프트는 Claude가 Scene별 영어로 작성한다.

다음 내용을 포함한다.

* 실제 상품 이미지 기준
* 세로형 9:16
* 현실적인 광고 영상
* 자연스러운 카메라 움직임
* 상품 중심 구도
* 장면 길이
* 배경
* 조명
* 행동
* 분위기
* 카메라 구도
* 카메라 이동

다음 내용을 금지한다.

* 영상 내 텍스트 생성
* 자막 생성
* 가격 생성
* 새로운 로고 생성
* 워터마크
* 상품 색상 변경
* 상품 형태 변경
* 버튼 및 구성 변경
* 상품에 없는 기능 추가
* 손가락 또는 신체 왜곡이 심한 복잡한 동작
* 과도한 회전
* 제품이 녹거나 변형되는 연출

가능한 경우 text-to-video보다 image-to-video를 우선 사용한다.

상품 이미지가 명확한 경우 해당 이미지를 참조 이미지로 사용한다.

AI 영상은 상품 외형 정확성보다 분위기 연출이 중요한 장면에만 사용한다.

---

# 14. Kling API 처리

Kling 영상 생성은 Scene 단위로 실행한다.

각 Scene은 독립적인 작업으로 관리한다.

Scene별 상태는 다음과 같다.

* `PENDING`
* `SUBMITTED`
* `PROCESSING`
* `COMPLETED`
* `FAILED`
* `CANCELLED`
* `CACHED`

Scene은 최대 동시 실행 개수를 설정할 수 있도록 한다.

초기 기본 동시 실행 개수는 2개로 한다.

Kling 실패 시 전체 광고를 다시 생성하지 않는다.

실패한 Scene만 재시도한다.

재시도 횟수와 간격은 설정 가능하게 한다.

동일한 입력 이미지, 프롬프트, 모델, 길이, 해상도 조합이 존재하면 캐시된 결과를 사용한다.

---

# 15. TTS 생성

TTS Provider는 교체 가능한 인터페이스로 구현한다.

초기 Provider는 프로젝트 환경에 맞는 하나만 연결한다.

TTS 입력은 전체 나레이션을 합친 한 번의 요청을 우선한다.

Scene별 타이밍이 필요한 경우 문장별 타임코드를 생성한다.

다음 결과를 저장한다.

* 전체 음성 파일
* 문장별 시작 시간
* 문장별 종료 시간
* 자막 타이밍
* 사용한 목소리
* 속도
* 감정 설정

나레이션이 변경되지 않았다면 기존 TTS를 재사용한다.

TTS 속도와 영상 길이가 맞지 않으면 다음 순서로 조정한다.

1. 문장 간 공백 조정
2. TTS 속도 미세 조정
3. Scene 길이 조정
4. Claude에 대본 축약 요청

---

# 16. 자막 생성

자막은 나레이션과 화면 전달력을 기준으로 생성한다.

나레이션 전체를 그대로 긴 문장으로 표시하지 않는다.

한 자막은 짧고 읽기 쉽게 구성한다.

다음 파일을 생성한다.

* `subtitle.json`
* `subtitle.srt`

자막에는 다음 정보를 저장한다.

* 시작 시간
* 종료 시간
* 텍스트
* 강조 단어
* 자막 위치
* 애니메이션 유형
* 폰트 크기

상품명, 가격, 할인율, CTA는 별도 텍스트 레이어로 분리한다.

---

# 17. BGM 및 효과음

상업적 사용이 허용된 로컬 음원만 사용한다.

BGM은 로컬 라이브러리에서 선택한다.

초기에는 AI 음악 생성을 사용하지 않는다.

BGM 종류를 다음과 같이 분류한다.

* 밝은 생활용품
* 빠른 리뷰형
* 감성형
* 프리미엄형
* 전자제품형
* 주방형
* 뷰티형
* 반려동물형

Claude가 상품과 광고 스타일에 맞는 BGM 태그를 선택하도록 한다.

BGM 볼륨은 나레이션을 방해하지 않게 자동 조절한다.

효과음은 다음 상황에 제한적으로 사용한다.

* Hook
* 핵심 기능 등장
* 가격 또는 혜택 등장
* CTA 등장
* Scene 전환

---

# 18. Remotion 영상 합성

최종 영상 합성은 Remotion을 중심으로 구현한다.

FFmpeg는 다음 용도로 사용한다.

* 영상 포맷 정규화
* 영상 및 오디오 인코딩
* 클립 자르기
* 볼륨 조절
* 최종 MP4 생성
* 썸네일 프레임 추출
* 영상 메타데이터 확인

최종 출력 규격은 다음과 같다.

* 해상도: 1080×1920
* 화면 비율: 9:16
* 형식: MP4
* 비디오 코덱: H.264
* 오디오 코덱: AAC
* 프레임레이트: 30fps
* 오디오 샘플레이트: 48kHz

합성 요소는 다음과 같다.

* 원본 상품 이미지
* Kling 생성 영상
* 자막
* 나레이션
* BGM
* 효과음
* 상품명
* 가격
* 핵심 특징
* CTA
* 쿠팡파트너스 고지 문구

Kling 생성 영상의 길이가 Scene 길이와 맞지 않으면 자연스럽게 컷, 속도 조정 또는 Freeze Frame을 적용한다.

---

# 19. 쿠팡파트너스 고지

다음 고지 문구를 설정값으로 관리한다.

```text
이 포스팅은 쿠팡파트너스 활동의 일환으로, 이에 따른 일정액의 수수료를 제공받습니다.
```

고지 문구는 다음 위치에 사용할 수 있도록 한다.

* 영상 내 마지막 Scene
* 영상 설명
* 고정댓글

사용자가 고지 표시 방식을 변경할 수 있도록 한다.

고지 문구가 누락되지 않도록 최종 렌더링 전에 검증한다.

---

# 20. 썸네일 생성

썸네일은 별도 AI 이미지 생성 없이 제작한다.

다음 요소를 조합한다.

* 대표 상품 이미지
* 영상 내 가장 선명한 프레임
* 짧은 Hook 문구
* Gradient
* Shadow
* Blur
* Outline
* Highlight
* 배경 대비 조정

썸네일 문구는 8~14자 내외로 생성한다.

썸네일에는 상품 가격이나 할인율을 사용자가 명시적으로 선택한 경우에만 표시한다.

---

# 21. 최종 산출물

광고 버전별로 다음 산출물을 저장한다.

```text
campaigns/
  {campaignId}/
    campaign.json
    storyboard.json
    prompts.json
    source-map.json
    subtitles/
      subtitle.json
      subtitle.srt
    audio/
      narration.mp3
      bgm.mp3
      mixed-audio.mp3
    scenes/
      scene-01/
        request.json
        result.mp4
      scene-02/
      scene-03/
    renders/
      draft.mp4
      final.mp4
      thumbnail.png
    metadata/
      youtube.json
      generation-cost.json
      checkpoints.json
      job-log.json
```

`youtube.json`에는 다음 내용을 저장한다.

* 제목
* 설명
* 해시태그
* 고정댓글
* 쿠팡파트너스 URL
* 고지 문구

---

# 22. 작업 큐

모든 장시간 작업은 Job Queue로 처리한다.

작업 유형은 다음과 같다.

* 상품 데이터 저장
* Claude 상품 분석
* 광고 기획 생성
* 스토리보드 생성
* Kling Scene 생성
* TTS 생성
* 자막 생성
* 영상 합성
* 썸네일 생성
* 최종 검증

Job 상태는 다음과 같다.

* `PENDING`
* `RUNNING`
* `COMPLETED`
* `FAILED`
* `RETRYING`
* `CANCELLED`
* `SKIPPED`
* `CACHED`

각 작업은 다음 정보를 저장한다.

* Job ID
* 상품 ID
* 광고 ID
* 작업 유형
* 진행률
* 현재 단계
* 시작 시각
* 종료 시각
* 재시도 횟수
* 오류 코드
* 오류 메시지
* 입력 해시
* 결과 파일 경로

프로젝트를 종료했다가 다시 실행해도 미완료 작업을 이어서 처리할 수 있도록 한다.

---

# 23. Checkpoint 영구 저장

각 단계가 완료될 때마다 Checkpoint를 생성한다.

Checkpoint 단계는 다음과 같다.

1. `PRODUCT_CAPTURED`
2. `PRODUCT_VALIDATED`
3. `PRODUCT_ANALYZED`
4. `CAMPAIGNS_CREATED`
5. `CAMPAIGN_SELECTED`
6. `STORYBOARD_CREATED`
7. `PROMPTS_CREATED`
8. `AI_SCENES_CREATED`
9. `TTS_CREATED`
10. `SUBTITLES_CREATED`
11. `DRAFT_RENDERED`
12. `FINAL_RENDERED`
13. `THUMBNAIL_CREATED`
14. `COMPLETED`

각 Checkpoint에는 다음을 저장한다.

* 단계명
* 완료 시각
* 입력 데이터 해시
* 출력 데이터 해시
* 생성 파일 경로
* 사용한 Provider
* 사용한 모델
* API 요청 ID
* 예상 비용
* 실제 사용량
* 오류 여부

작업 실패 후 재실행 시 마지막으로 완료된 Checkpoint 다음 단계부터 시작한다.

처음부터 다시 실행하지 않는다.

---

# 24. 캐시 정책

캐시 키는 다음 항목을 조합하여 생성한다.

* 상품 데이터 해시
* 원본 이미지 해시
* 광고 기획 ID
* 프롬프트 해시
* Provider
* 모델
* 영상 길이
* 해상도
* TTS 설정
* 템플릿 버전

다음 조건이 같으면 기존 결과를 재사용한다.

* 동일 상품
* 동일 이미지
* 동일 프롬프트
* 동일 모델
* 동일 길이
* 동일 해상도
* 동일 TTS 설정

캐시된 결과가 존재하면 API를 호출하지 않는다.

캐시 파일이 손상되었거나 존재하지 않는 경우에만 다시 생성한다.

---

# 25. 변경 감지 및 부분 재생성

입력 데이터와 설정의 변경 범위를 감지한다.

변경 항목별로 필요한 단계만 다시 수행한다.

## 제목 변경

* 영상 재생성 금지
* 제목 메타데이터만 수정

## 설명 또는 해시태그 변경

* 영상 재생성 금지
* 업로드 메타데이터만 수정

## CTA 텍스트 변경

* Kling 재호출 금지
* TTS가 변경되지 않으면 최종 합성만 실행
* 나레이션 CTA도 변경되면 TTS와 최종 합성만 실행

## 자막 변경

* Kling 재호출 금지
* 자막 및 최종 합성만 실행

## BGM 변경

* Kling과 Claude 재호출 금지
* 오디오 믹싱과 최종 합성만 실행

## TTS 목소리 변경

* Kling과 Claude 재호출 금지
* TTS와 최종 합성만 실행

## Hook 문구만 변경

* Hook이 텍스트 자막만 변경되는 경우 합성만 실행
* Hook 나레이션 변경 시 TTS와 합성 실행
* Hook AI 영상 프롬프트 변경 시 해당 Scene만 Kling 재생성

## 특정 Scene 수정

* 수정된 Scene만 재생성
* 이후 최종 합성만 실행

## 광고 스타일 변경

* 상품 분석 재사용
* 새로운 광고 기획과 스토리보드만 생성
* 필요한 Scene만 생성

## 상품 이미지 변경

* 변경된 이미지를 사용하는 Scene만 무효화
* 해당 Scene과 최종 합성만 재실행

## 상품 자체 변경

* 새로운 상품 ID 생성
* 전체 프로세스 신규 실행

---

# 26. 비용 기록

각 API 호출 비용과 사용량을 기록한다.

다음 정보를 저장한다.

* Claude 입력 토큰
* Claude 출력 토큰
* Claude 예상 비용
* Kling 생성 횟수
* Kling Scene 길이
* Kling 사용 크레딧
* TTS 문자 수 또는 생성 시간
* 광고 한 편당 총 예상 비용
* 상품별 누적 비용
* 실패 재시도 비용
* 캐시로 절약한 예상 비용

영상 생성 전 예상 비용을 계산하여 표시한다.

사용자가 실행을 선택한 광고 기획안만 비용을 발생시키도록 한다.

설정한 최대 비용을 초과할 경우 Kling 실행 전에 중단한다.

---

# 27. 생성 모드

다음 생성 모드를 제공한다.

## 초저비용 모드

* Kling 사용 안 함
* 원본 이미지와 Remotion 효과만 사용
* Claude 1회
* TTS 1회
* 15~20초 권장

## 기본 모드

* Kling Scene 최대 1개
* 원본 이미지 중심
* 20초 권장

## 고품질 모드

* Kling Scene 최대 2개
* 원본 이미지와 AI 영상 혼합
* 20~30초 권장

기본값은 기본 모드로 한다.

Kling Scene 개수는 Claude 추천보다 사용자의 비용 제한을 우선한다.

---

# 28. 광고 재활용

이미 완성된 광고를 기반으로 저비용 파생 영상을 생성할 수 있도록 한다.

지원 항목은 다음과 같다.

* Hook 변경
* CTA 변경
* 제목 변경
* 자막 스타일 변경
* BGM 변경
* TTS 목소리 변경
* 영상 길이 변경
* Scene 순서 변경
* 가격 표시 여부 변경
* 원본 이미지 교체
* 15초 버전 생성
* 20초 버전 생성
* 30초 버전 생성

기존 Kling Scene은 최대한 재사용한다.

같은 상품으로 여러 영상이 필요할 경우 전체 장면을 새로 생성하지 않는다.

---

# 29. 최종 품질 검증

렌더링 완료 전 자동 검증을 수행한다.

검증 항목은 다음과 같다.

* 영상 해상도
* 영상 비율
* 영상 길이
* 오디오 존재 여부
* 자막 잘림 여부
* 텍스트 화면 밖 이탈 여부
* 검은 화면 여부
* 깨진 Scene 여부
* 무음 구간 여부
* 상품 이미지 존재 여부
* CTA 존재 여부
* 파트너스 고지 존재 여부
* 제목 존재 여부
* 설명 존재 여부
* 해시태그 존재 여부
* 파트너스 URL 존재 여부

검증 실패 시 완료 상태로 변경하지 않는다.

수정 가능한 문제는 자동 보정 후 다시 렌더링한다.

---

# 30. 오류 처리

모든 외부 API 오류를 사용자에게 이해 가능한 상태로 표시한다.

## Claude 실패

* 최대 재시도
* JSON Schema 오류 시 수정 요청
* 마지막 유효 결과 보존

## Kling 실패

* 해당 Scene만 재시도
* 재시도 실패 시 원본 이미지 Scene으로 대체 가능
* 대체 여부를 기록

## TTS 실패

* 해당 음성만 재시도
* 실패 시 다른 로컬 또는 대체 Provider 사용 가능

## FFmpeg 또는 Remotion 실패

* 로그 저장
* 임시 파일 정리
* 합성 단계만 재실행

## Extension 추출 실패

* 수동 입력 화면 제공
* 사용자가 이미지와 상품 정보를 직접 입력해 계속 진행 가능

오류가 발생해도 완료된 중간 결과는 삭제하지 않는다.

---

# 31. 로그

구조화된 로그를 남긴다.

다음 로그를 구분한다.

* 사용자 작업 로그
* Claude 요청 로그
* Kling 요청 로그
* TTS 요청 로그
* FFmpeg 로그
* Remotion 로그
* Job Queue 로그
* Checkpoint 로그
* 비용 로그
* 오류 로그

API 키, 쿠키, 인증 토큰은 로그에 저장하지 않는다.

---

# 32. 환경 설정

민감한 정보는 `.env` 또는 로컬 환경변수로 관리한다.

예시 환경변수는 다음과 같다.

```text
CLAUDE_API_KEY=
CLAUDE_MODEL=
KLING_API_KEY=
KLING_MODEL=
TTS_PROVIDER=
TTS_API_KEY=
FFMPEG_PATH=
WORKSPACE_PATH=
MAX_PARALLEL_SCENES=2
MAX_RETRY_COUNT=3
DEFAULT_GENERATION_MODE=BASIC
MAX_COST_PER_VIDEO=
```

`.env.example`을 제공한다.

실제 `.env`는 Git에 포함하지 않는다.

---

# 33. 보안

* API 키를 프론트엔드에 노출하지 않는다.
* Chrome Extension에 Claude 및 Kling API 키를 저장하지 않는다.
* 모든 AI API 호출은 로컬 백엔드에서 처리한다.
* 상품 페이지 쿠키를 로컬 백엔드로 전송하지 않는다.
* 로컬 API는 기본적으로 localhost에서만 접근 가능하게 한다.
* 외부 네트워크 공개가 필요한 경우 별도 인증을 적용한다.
* 업로드된 파일의 확장자, MIME Type, 크기를 검증한다.
* 사용자 입력을 파일 경로로 직접 사용하지 않는다.
* FFmpeg 명령어에 사용자 문자열을 직접 연결하지 않는다.

---

# 34. 데이터베이스 주요 엔티티

최소한 다음 엔티티를 설계한다.

## Product

* id
* name
* brand
* category
* price
* originalPrice
* productUrl
* affiliateUrl
* description
* extractedAt
* createdAt
* updatedAt
* productHash

## ProductAsset

* id
* productId
* type
* localPath
* sourceUrl
* fileHash
* width
* height
* selected
* createdAt

## ProductAnalysis

* id
* productId
* analysisJson
* inputHash
* model
* tokenUsage
* createdAt

## Campaign

* id
* productId
* style
* concept
* status
* duration
* generationMode
* selected
* createdAt
* updatedAt

## Storyboard

* id
* campaignId
* storyboardJson
* version
* createdAt

## Scene

* id
* campaignId
* orderNo
* sourceType
* duration
* caption
* narration
* prompt
* status
* assetPath
* inputHash
* createdAt
* updatedAt

## GenerationJob

* id
* productId
* campaignId
* sceneId
* jobType
* status
* progress
* retryCount
* inputHash
* errorCode
* errorMessage
* startedAt
* completedAt

## Checkpoint

* id
* productId
* campaignId
* stage
* inputHash
* outputHash
* outputPath
* createdAt

## RenderOutput

* id
* campaignId
* videoPath
* thumbnailPath
* metadataPath
* duration
* width
* height
* status
* createdAt

## CostUsage

* id
* productId
* campaignId
* provider
* model
* operation
* usage
* estimatedCost
* createdAt

---

# 35. Provider 추상화

Claude, Kling, TTS는 인터페이스로 분리한다.

예시 인터페이스는 다음과 같다.

```text
ProductAnalysisProvider
CampaignPlanningProvider
StoryboardProvider
VideoGenerationProvider
TextToSpeechProvider
RenderProvider
StorageProvider
```

현재는 Claude와 Kling만 구현하되, 나중에 다른 Provider로 교체할 수 있도록 한다.

사용자 화면에 여러 AI 선택 기능을 만들 필요는 없다.

내부 구현만 교체 가능하게 구성한다.

---

# 36. 프롬프트 파일 분리

Claude 프롬프트를 코드에 직접 길게 작성하지 않는다.

다음과 같이 별도 파일로 관리한다.

```text
prompts/
  product-analysis.md
  campaign-generation.md
  storyboard-generation.md
  storyboard-repair.md
  metadata-generation.md
  subtitle-generation.md
```

프롬프트마다 버전을 관리한다.

생성 결과에는 사용한 프롬프트 버전을 저장한다.

---

# 37. 테스트

다음 테스트를 작성한다.

## 단위 테스트

* 상품 데이터 검증
* 이미지 중복 제거
* 입력 해시 생성
* 캐시 키 생성
* 변경 감지
* 부분 재생성 범위 계산
* JSON Schema 검증
* 자막 타이밍 계산
* 비용 계산
* Job 상태 전환

## 통합 테스트

* Claude Mock 응답 처리
* Kling Mock Job Polling
* TTS Mock 생성
* Remotion 렌더링
* FFmpeg 합성
* Checkpoint 재개
* 실패 Scene만 재시도
* 캐시 결과 재사용

## E2E 테스트

* Extension 상품 추출
* 상품 저장
* 광고 기획 생성
* 기획안 선택
* 영상 생성
* 최종 MP4 확인

외부 API를 실제로 호출하지 않는 Mock 모드를 제공한다.

---

# 38. 개발 진행 순서

다음 순서로 구현한다.

## 1단계

* 프로젝트 기본 구조
* 환경설정
* 데이터베이스
* 파일 저장소
* Job Queue
* Checkpoint
* 로그

## 2단계

* Chrome Extension
* 상품 DOM 추출
* 로컬 API 전송
* 상품 및 이미지 저장
* 수동 입력 대체 처리

## 3단계

* Claude Provider
* 상품 분석
* 광고 기획안 생성
* JSON Schema 검증
* 결과 저장 및 캐시

## 4단계

* 기획안 선택
* 스토리보드 생성
* Scene 분리
* 비용 예상 계산

## 5단계

* Kling Provider
* Scene별 생성
* 비동기 Polling
* 실패 Scene 재시도
* Scene 캐시

## 6단계

* TTS Provider
* 자막 생성
* 오디오 타이밍 처리

## 7단계

* Remotion 템플릿
* FFmpeg 후처리
* 최종 MP4
* 썸네일
* 메타데이터 파일 생성

## 8단계

* 부분 재생성
* 광고 재활용
* 비용 기록
* 품질 검증
* Mock 테스트
* 전체 E2E 테스트

각 단계 완료 후 실행 가능한 상태를 유지한다.

---

# 39. 구현 완료 기준

다음 시나리오가 정상 동작해야 완료로 간주한다.

1. 로컬 프로젝트를 실행한다.
2. 쿠팡 상품 페이지에서 Extension 버튼을 누른다.
3. 상품 정보와 이미지가 로컬 프로젝트에 전달된다.
4. 쿠팡파트너스 URL을 입력한다.
5. Claude가 상품을 분석한다.
6. 서로 다른 광고 기획안 3개 이상이 생성된다.
7. 사용자가 하나를 선택한다.
8. 20초 스토리보드가 생성된다.
9. 생성 모드에 따라 Kling Scene이 0~2개 생성된다.
10. 원본 이미지 Scene과 AI Scene이 합성된다.
11. TTS, 자막, BGM, CTA, 고지 문구가 추가된다.
12. 1080×1920 MP4가 생성된다.
13. 썸네일이 생성된다.
14. 제목, 설명, 해시태그, 고정댓글이 생성된다.
15. 모든 중간 결과가 저장된다.
16. 프로젝트 종료 후 다시 실행해도 작업을 이어갈 수 있다.
17. CTA만 변경하면 Kling과 Claude를 재호출하지 않고 영상만 다시 합성된다.
18. 특정 AI Scene 실패 시 해당 Scene만 다시 생성된다.
19. 동일 설정으로 재실행하면 캐시된 결과를 사용한다.
20. 영상 한 편의 예상 API 비용을 확인할 수 있다.

---

# 40. 최종 개발 원칙

* 동작하는 기능을 우선 구현한다.
* 불필요한 복잡성을 추가하지 않는다.
* 초기 버전에 클라우드 인프라를 도입하지 않는다.
* 로컬에서 단독 실행 가능하게 한다.
* 외부 API 장애가 전체 프로젝트를 망가뜨리지 않게 한다.
* 모든 단계의 결과를 파일과 데이터베이스에 저장한다.
* 동일 작업을 두 번 수행하지 않는다.
* 전체 재생성을 기본 동작으로 만들지 않는다.
* 상품 분석은 상품당 한 번만 수행한다.
* 광고 기획은 텍스트로 먼저 생성한다.
* 사용자가 선택한 광고만 비용을 발생시킨다.
* Kling은 꼭 필요한 장면에만 사용한다.
* 원본 상품 이미지를 최대한 활용한다.
* 텍스트와 가격은 Remotion으로 정확하게 표시한다.
* 결과물이 실제 쿠팡파트너스 광고 제작에 바로 활용 가능해야 한다.
* 우선 모든 핵심 프로세스를 완성한 뒤 세부 디자인을 다듬는다.
* 구현 중 불명확한 부분은 가장 단순하고 비용이 적은 방식으로 결정한다.
* 기존 코드가 있다면 구조와 규칙을 먼저 분석한 뒤 최소한의 변경으로 통합한다.

위 요구사항을 기준으로 현재 프로젝트 구조를 먼저 분석하고, 구현 계획을 작성한 뒤 단계별로 실제 코드를 작성한다.

문서나 설계안만 작성하고 종료하지 말고 실제 실행 가능한 기능까지 구현한다.

각 단계가 끝날 때마다 다음 내용을 보고한다.

* 구현한 기능
* 생성 또는 수정한 파일
* 실행 방법
* 테스트 결과
* 남은 작업
* 발견된 문제


프로젝트의 최우선 목표는

개발 속도가 아니라

실제 수익이 발생하는 고품질 쇼핑 광고를 만드는 것이다.

비용 절감은 품질을 해치지 않는 범위에서만 수행한다.

모든 최적화는

광고 품질 유지

↓

생성 시간 단축

↓

비용 절감

순으로 적용한다.
