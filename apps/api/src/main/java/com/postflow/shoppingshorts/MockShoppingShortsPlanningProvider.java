package com.postflow.shoppingshorts;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "shopping-shorts.planning.provider", havingValue = "mock", matchIfMissing = true)
public class MockShoppingShortsPlanningProvider implements ShoppingShortsPlanningProvider {
    @Override
    public String id() {
        return "local-mock";
    }

    @Override
    public PlanningResult generate(ShoppingShortsProductDocument product) {
        ShoppingShortsDtos.ProductAnalysis analysis = buildAnalysis(product);
        List<ShoppingShortsDtos.CampaignCandidate> candidates = buildCampaignCandidates(product, analysis);
        ShoppingShortsDtos.CampaignGenerationResponse response =
                new ShoppingShortsDtos.CampaignGenerationResponse(product.productId(), analysis, candidates);
        return new PlanningResult(response, id(), "rule-based-v1", 0, 0);
    }

    private ShoppingShortsDtos.ProductAnalysis buildAnalysis(ShoppingShortsProductDocument product) {
        List<String> features = product.features() == null || product.features().isEmpty()
                ? List.of("상품 상세 설명 기반 핵심 장점 보완 필요")
                : product.features();
        List<String> styles = List.of("충동구매 훅형", "실사용 욕구형", "신뢰 전환형");
        return new ShoppingShortsDtos.ProductAnalysis(
                product.productId(),
                List.of("구매 직전 비교", "일상 사용 상황", "짧은 쇼츠 탐색"),
                features.stream().limit(4).toList(),
                List.of("상품 정보에 없는 효과나 성능은 말하지 않음", "가격은 추출 시점 기준으로만 표시"),
                styles,
                List.of("아직도 불편해요?", "이거 왜 난리야?", "직접 써봤습니다", "이 차이 큽니다", "지금 필요해요"),
                20);
    }

    private List<ShoppingShortsDtos.CampaignCandidate> buildCampaignCandidates(
            ShoppingShortsProductDocument product,
            ShoppingShortsDtos.ProductAnalysis analysis) {
        String firstFeature = analysis.sellingPoints().isEmpty() ? "확인된 특징" : analysis.sellingPoints().getFirst();
        String audience = product.category() == null ? "실용적인 쇼핑을 원하는 시청자" : product.category() + " 구매를 고민하는 시청자";
        return List.of(
                new ShoppingShortsDtos.CampaignCandidate(
                        "campaign-review",
                        "실사용 욕구형",
                        "제품 클로즈업과 사용 장면을 빠르게 교차해 지금 필요한 물건처럼 보이게 만드는 쇼츠",
                        "이거 하나로 체감이 달라져요",
                        audience,
                        analysis.sellingPoints(),
                        "지금 쿠팡에서 바로 확인하세요",
                        20,
                        2,
                        3,
                        "MEDIUM",
                        "LOW",
                        88,
                        "핵심 사용 장면만 AI로 만들고 나머지는 원본 이미지 편집으로 제품 신뢰도를 유지합니다."),
                new ShoppingShortsDtos.CampaignCandidate(
                        "campaign-problem-solution",
                        "충동구매 훅형",
                        firstFeature + "을 첫 3초에 크게 보여주고 일상 사용 욕구로 연결",
                        "보자마자 왜 필요한지 보여드릴게요",
                        audience,
                        analysis.sellingPoints(),
                        "옵션과 가격은 구매 페이지에서 확인하세요",
                        20,
                        2,
                        3,
                        "MEDIUM",
                        "MEDIUM",
                        82,
                        "후킹은 AI 장면으로 살리고 제품 디테일은 원본 이미지로 보여줘 과장 위험을 줄입니다."),
                new ShoppingShortsDtos.CampaignCandidate(
                        "campaign-checklist",
                        "신뢰 전환형",
                        "제품 디테일, 인증/구성, 사용 맥락을 영상으로 보여주며 구매 불안을 줄이는 쇼츠",
                        "놓치면 후회할 포인트만 볼게요",
                        audience,
                        analysis.sellingPoints(),
                        "쿠팡파트너스 링크에서 최신 조건을 확인하세요",
                        20,
                        1,
                        4,
                        "LOW",
                        "LOW",
                        79,
                        "Kling 호출을 최소화하고 구성/디테일/CTA를 원본 이미지 기반으로 빠르게 전달합니다.")
        );
    }
}
