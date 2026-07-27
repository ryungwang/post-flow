package com.postflow.ai.content.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 쿠팡파트너스 등 제휴(어필리에이트) 콘텐츠 생성 요청.
 *
 * <p>링크·고지문·subId는 <b>모델이 아니라 서버가 결정적으로</b> 붙인다(대가성 고지는 법적 의무라
 * 모델 응답에 맡기면 누락 위험). {@code productFeatures}는 실제 특징을 근거로 줘서 스펙·후기 날조를 줄인다.
 *
 * @param productName     제품명(필수)
 * @param productFeatures 실제 특징·장점(선택) — 있으면 이걸 근거로만 쓰게 해 날조를 줄인다
 * @param affiliateLink   내 쿠팡파트너스 링크(필수) — 상품마다 다르므로 매번 입력
 * @param subIdPrefix     subId 접두어(선택) — 최종 subId = prefix_platform(비면 platform)
 * @param tone            톤
 * @param quantity        생성 개수(1~30)
 * @param platform        발행 대상 플랫폼(THREADS/INSTAGRAM/... ) — 플랫폼별 링크 처리·글자수 반영
 */
public record GenerateAffiliateRequest(
        @NotBlank String productName,
        String productFeatures,
        @NotBlank String affiliateLink,
        String subIdPrefix,
        String tone,
        @Min(1) @Max(30) int quantity,
        String platform
) {
    public String toneOrDefault() {
        return tone == null || tone.isBlank() ? "Friendly" : tone;
    }

    public String featuresOrNull() {
        return productFeatures == null || productFeatures.isBlank() ? null : productFeatures.trim();
    }
}
