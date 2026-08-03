package com.postflow.coupang;

import com.postflow.coupang.CoupangPartnersClient.CoupangProduct;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 쿠팡 파트너스 Open API — 딥링크 변환 + 상품 소싱(골드박스·베스트·검색). SNS 제휴 소재를 쿠팡 실데이터로 고른다.
 * subId는 생성 단계에서 앱이 플랫폼별로 덧붙이므로 여기선 base 링크만 다룬다.
 */
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

    /** 오늘의 골드박스(특가) — 지금 미는 딜. */
    @GetMapping("/goldbox")
    public List<CoupangProduct> goldbox(@AuthenticationPrincipal Long userId) {
        return client.goldbox();
    }

    /** 카테고리 베스트 — categoryId는 쿠팡 고정 코드(예: 1010 가전디지털). */
    @GetMapping("/best")
    public List<CoupangProduct> best(@AuthenticationPrincipal Long userId,
                                     @RequestParam String categoryId,
                                     @RequestParam(defaultValue = "20") int limit) {
        if (!StringUtils.hasText(categoryId)) {
            throw new IllegalArgumentException("카테고리를 선택해 주세요.");
        }
        return client.bestCategories(categoryId.trim(), limit);
    }

    /** 키워드 상품 검색. */
    @GetMapping("/search")
    public List<CoupangProduct> search(@AuthenticationPrincipal Long userId,
                                       @RequestParam String keyword,
                                       @RequestParam(defaultValue = "20") int limit) {
        if (!StringUtils.hasText(keyword)) {
            throw new IllegalArgumentException("검색어를 입력해 주세요.");
        }
        return client.search(keyword.trim(), limit);
    }

    public record DeeplinkRequest(String url) {
    }
}
