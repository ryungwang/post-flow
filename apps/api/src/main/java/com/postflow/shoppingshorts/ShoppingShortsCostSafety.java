package com.postflow.shoppingshorts;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ShoppingShortsCostSafety {
    private final boolean liveApiEnabled;

    public ShoppingShortsCostSafety(
            @Value("${shopping-shorts.live-api-enabled:false}") boolean liveApiEnabled) {
        this.liveApiEnabled = liveApiEnabled;
    }

    public boolean liveApiEnabled() {
        return liveApiEnabled;
    }

    public String mode() {
        return liveApiEnabled ? "LIVE_API_UNLOCKED" : "SAFE_MOCK_LOCKED";
    }

    public void requireLiveApiEnabled(String provider, String operation) {
        if (!liveApiEnabled) {
            throw new IllegalStateException(
                    "쇼핑쇼츠 실 API 호출이 잠겨 있습니다. 비용 발생 가능 작업(%s/%s)을 실행하려면 SHOPPING_SHORTS_LIVE_API_ENABLED=true를 명시적으로 설정하세요."
                            .formatted(provider, operation));
        }
    }
}
