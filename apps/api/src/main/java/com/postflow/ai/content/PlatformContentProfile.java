package com.postflow.ai.content;

import com.postflow.social.SocialProvider;

/**
 * 플랫폼별 콘텐츠 생성 특성. 같은 주제라도 각 SNS의 알고리즘·제약이 달라 글을 다르게 뽑아야 한다.
 *
 * <ul>
 *   <li>{@code maxChars} — 하드 제한(초과하면 발행이 잘리거나 실패). 코드포인트 기준으로 잰다.</li>
 *   <li>{@code idealMin/idealMax} — 도달·가독성이 좋은 길이 구간(스코어러의 length 가중치가 이걸 씀).</li>
 *   <li>{@code hashtagMin/Max} — 그 플랫폼에서 유효한 해시태그 수.</li>
 *   <li>{@code imageCentric} — 이미지가 도달을 좌우하는가(IG). 캡션 훅 전략이 달라진다.</li>
 *   <li>{@code algorithmGuidance} — 프롬프트에 주입되는 플랫폼 알고리즘 특성(한국어).</li>
 * </ul>
 */
public record PlatformContentProfile(
        SocialProvider provider,
        String displayName,
        int maxChars,
        int idealMin,
        int idealMax,
        int hashtagMin,
        int hashtagMax,
        boolean imageCentric,
        String algorithmGuidance
) {

    /** 요청의 platform 문자열(대소문자 무관)로 프로필을 찾는다. 없거나 미지원이면 Threads 기본. */
    public static PlatformContentProfile fromRequest(String platform) {
        if (platform == null || platform.isBlank()) {
            return of(SocialProvider.THREADS);
        }
        try {
            return of(SocialProvider.valueOf(platform.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return of(SocialProvider.THREADS);
        }
    }

    public static PlatformContentProfile of(SocialProvider p) {
        return switch (p) {
            case THREADS -> new PlatformContentProfile(p, "Threads", 500, 250, 480, 3, 5, false,
                    "Threads는 답글·리포스트가 도달을 키운다. 첫 줄 훅이 노출을 좌우하고, "
                            + "대화를 부르는 마무리(질문·공감 유도)가 유리하다. 줄바꿈으로 스캔하기 쉽게.");
            case BLUESKY -> new PlatformContentProfile(p, "Bluesky", 300, 120, 280, 1, 3, false,
                    "Bluesky는 300자 제한이 엄격하다(절대 초과 금지). 시간순 피드에 약한 알고리즘이라 "
                            + "간결하고 임팩트 있게 — 한 호흡에 읽히게. 해시태그는 1-3개면 충분하고 본문에 자연스럽게 녹여라.");
            case MASTODON -> new PlatformContentProfile(p, "Mastodon", 500, 180, 480, 3, 5, false,
                    "Mastodon은 추천 알고리즘이 없어 해시태그가 발견의 핵심이다 — 주제를 정확히 대표하는 "
                            + "해시태그 3-5개가 필수. 커뮤니티가 광고티·과장·낚시를 싫어하니 진솔하고 담백하게 쓰라.");
            case INSTAGRAM -> new PlatformContentProfile(p, "Instagram", 2200, 100, 600, 5, 10, true,
                    "Instagram은 이미지가 도달의 대부분을 결정한다. 캡션 첫 줄은 '...더 보기'로 접히기 전에 "
                            + "보이는 강력한 훅이어야 한다. 저장·공유를 부르는 구체적 가치를 담고, 해시태그는 5-10개로 "
                            + "주제·니치를 정확히 겨냥하라(캡션 끝에 모아서).");
            case FACEBOOK -> new PlatformContentProfile(p, "Facebook", 2000, 80, 600, 0, 2, false,
                    "Facebook은 네이티브 콘텐츠를 선호하고 외부 링크 글은 덜 노출한다. 대화·공감·태그를 부르는 "
                            + "친근한 어조로. 해시태그는 최소(0-2개)로 절제하라.");
            case LINKEDIN -> new PlatformContentProfile(p, "LinkedIn", 3000, 150, 900, 3, 5, false,
                    "LinkedIn은 전문성·인사이트 중심이다. 첫 2줄이 '...더 보기' 전에 보이는 훅이어야 한다. "
                            + "1인칭 경험·관점으로 신뢰를 쌓고, 실질적 교훈을 담아라. 해시태그 3-5개(전문 분야 태그).");
        };
    }

    /** 코드포인트 기준 하드 제한(이모지 서러게이트를 정확히 셈). 초과분은 잘라낸다. */
    public String clamp(String content) {
        if (content == null) {
            return null;
        }
        int cp = content.codePointCount(0, content.length());
        if (cp <= maxChars) {
            return content;
        }
        int end = content.offsetByCodePoints(0, maxChars);
        return content.substring(0, end);
    }
}
