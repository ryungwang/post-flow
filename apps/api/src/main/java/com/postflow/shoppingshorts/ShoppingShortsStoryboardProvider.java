package com.postflow.shoppingshorts;

public interface ShoppingShortsStoryboardProvider {
    String id();

    StoryboardResult generate(ShoppingShortsProductDocument product, ShoppingShortsDtos.CampaignCandidate campaign);

    record StoryboardResult(
            ShoppingShortsDtos.StoryboardResponse response,
            String provider,
            String model,
            long inputTokens,
            long outputTokens
    ) {
    }
}
