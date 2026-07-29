package com.postflow.shoppingshorts;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@ConditionalOnProperty(name = "shopping-shorts.storyboard.provider", havingValue = "mock", matchIfMissing = true)
public class MockShoppingShortsStoryboardProvider implements ShoppingShortsStoryboardProvider {
    private static final String DISCLOSURE = "이 포스팅은 쿠팡파트너스 활동의 일환으로, 이에 따른 일정액의 수수료를 제공받습니다.";

    @Override
    public String id() {
        return "local-mock";
    }

    @Override
    public StoryboardResult generate(ShoppingShortsProductDocument product, ShoppingShortsDtos.CampaignCandidate campaign) {
        List<String> features = campaign.sellingPoints().isEmpty()
                ? List.of(product.productName(), "상세 정보 확인 필요")
                : campaign.sellingPoints();
        List<ShoppingShortsDtos.StoryboardScene> scenes = List.of(
                scene(product, "scene-01", 1, "Hook", 3.0, "ORIGINAL_IMAGE",
                        "상품을 첫 화면에 크게 보여주고 불편 상황을 짧게 건드립니다.", campaign.hook(),
                        "Cut", "push-in match cut"),
                scene(product, "scene-02", 2, "Product Reveal", 4.0, "COMPOSITE",
                        "대표 이미지를 빠르게 확대하고 핵심 특징을 한 줄로 강조합니다.", features.getFirst(),
                        "push-in match cut", "feature highlight wipe"),
                scene(product, "scene-03", 3, "Use Demo", 4.0, "AI_VIDEO",
                        features.size() > 1 ? features.get(1) : features.getFirst(),
                        features.size() > 1 ? features.get(1) : features.getFirst(),
                        "feature highlight wipe", "close-up continuation"),
                scene(product, "scene-04", 4, "Proof", 4.0, "ORIGINAL_IMAGE",
                        "제품을 가까이 보여주며 확인된 특징 정리", "확인된 특징만 빠르게 보여드릴게요.",
                        "close-up continuation", "CTA card reveal"),
                scene(product, "scene-05", 5, "CTA", campaign.recommendedDuration() == 15 ? 0.0 : 5.0, "COMPOSITE",
                        campaign.cta(), campaign.cta(),
                        "CTA card reveal", "End card")
        ).stream().filter(s -> s.duration() > 0).toList();
        String title = product.productName() + " 쇼츠 - " + campaign.style();
        ShoppingShortsDtos.YoutubeMetadata youtube = new ShoppingShortsDtos.YoutubeMetadata(
                title,
                campaign.concept() + "\n\n" + DISCLOSURE,
                List.of("#쿠팡파트너스", "#쇼핑추천", "#쇼핑쇼츠"),
                DISCLOSURE + "\n" + product.affiliateUrl(),
                product.affiliateUrl(),
                DISCLOSURE);
        ShoppingShortsDtos.StoryboardResponse response = new ShoppingShortsDtos.StoryboardResponse(
                product.productId(),
                campaign.campaignId(),
                title,
                campaign.style(),
                campaign.hook(),
                campaign.recommendedDuration(),
                scenes,
                youtube,
                DISCLOSURE);
        return new StoryboardResult(response, id(), "rule-based-v1", 0, 0);
    }

    private ShoppingShortsDtos.StoryboardScene scene(
            ShoppingShortsProductDocument product,
            String sceneId,
            int order,
            String purpose,
            double duration,
            String sourceType,
            String visualDescription,
            String narration,
            String transitionIn,
            String transitionOut) {
        String productHint = StringUtils.hasText(product.category()) ? product.category() : product.productName();
        String klingPrompt = """
                Vertical 9:16 realistic shopping ad video, image-to-video from the provided product image.
                Keep the exact product design, shape, color, and logo unchanged.
                The product must remain clearly visible and centered as the main subject.
                Show natural handheld camera movement and real usage context for %s.
                No generated text, no subtitles, no price text, no watermark, no logo changes.
                """.formatted(productHint);
        return new ShoppingShortsDtos.StoryboardScene(
                sceneId,
                order,
                purpose,
                duration,
                sourceType,
                product.sourceImages() == null ? List.of() : product.sourceImages(),
                visualDescription,
                "Medium close-up",
                "AI_VIDEO".equals(sourceType) ? "Natural handheld push-in" : "fast zoom, highlight sweep, crop animation",
                transitionIn,
                transitionOut,
                shortCaption(narration),
                narration,
                order == 1 ? "Soft hit" : "",
                klingPrompt,
                "text, captions, watermark, logo change, color change, deformed product, wrong product shape",
                "AI_VIDEO".equals(sourceType));
    }

    private String shortCaption(String text) {
        if (text == null) {
            return "";
        }
        String value = text.replaceAll("\\s+", " ").trim();
        return value.length() <= 14 ? value : value.substring(0, 14);
    }
}
