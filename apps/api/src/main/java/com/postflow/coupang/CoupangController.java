package com.postflow.coupang;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 쿠팡 파트너스 Open API — 상품 URL을 제휴 딥링크로 변환한다(subId는 앱이 플랫폼별로 덧붙임). */
@RestController
@RequestMapping("/coupang")
public class CoupangController {

    private final CoupangPartnersClient client;

    public CoupangController(CoupangPartnersClient client) {
        this.client = client;
    }

    @PostMapping("/deeplink")
    public Map<String, String> deeplink(@AuthenticationPrincipal Long userId, @RequestBody DeeplinkRequest req) {
        if (req == null || !StringUtils.hasText(req.url())) {
            throw new IllegalArgumentException("쿠팡 상품 URL을 입력해 주세요.");
        }
        String shortUrl = client.deeplink(req.url().trim(), null);
        return Map.of("shortUrl", shortUrl);
    }

    public record DeeplinkRequest(String url) {
    }
}
