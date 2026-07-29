package com.postflow.shoppingshorts;

public interface ShoppingShortsPlanningProvider {
    String id();

    PlanningResult generate(ShoppingShortsProductDocument product);

    record PlanningResult(
            ShoppingShortsDtos.CampaignGenerationResponse response,
            String provider,
            String model,
            long inputTokens,
            long outputTokens
    ) {
    }
}
