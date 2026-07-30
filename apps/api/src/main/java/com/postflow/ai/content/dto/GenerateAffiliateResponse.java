package com.postflow.ai.content.dto;

import java.util.List;

/**
 * 제휴 콘텐츠 생성 응답. 각 카드의 본문엔 이미 (링크 친화 플랫폼이면) 링크 + 대가성 고지문이
 * 붙어 있다. {@code linkWithSubId}는 subId가 부착된 최종 링크로, 링크를 본문에 못 넣는 플랫폼
 * (Instagram 등)에서 사용자가 <b>프로필(bio) 링크</b>로 넣을 수 있게 별도로 돌려준다.
 *
 * @param cards         본문(링크·고지문 포함 완성형) 카드들
 * @param subId         이 플랫폼에 부착된 subId(쿠팡 리포트에서 채널별 실적 분리용)
 * @param linkWithSubId subId가 붙은 최종 제휴 링크
 * @param linkInBody    본문에 링크를 넣었는지(false면 프로필 링크로 사용 안내)
 */
public record GenerateAffiliateResponse(
        List<GeneratedCard> cards,
        String subId,
        String linkWithSubId,
        boolean linkInBody,
        String disclosure,        // 대가성 고지문(항상 제공). disclosureInBody=false면 본문엔 없고 첫 댓글로 써야 함.
        boolean disclosureInBody, // true=고지문이 본문에 포함됨 / false=본문에 없음(발행 시 첫 댓글로)
        String provider,
        String model
) {
}
