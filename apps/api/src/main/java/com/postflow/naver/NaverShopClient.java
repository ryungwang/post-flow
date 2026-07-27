package com.postflow.naver;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 네이버 쇼핑 검색(v1/search/shop) 클라이언트. 키워드로 실제 상품(제목·브랜드·카테고리·가격·이미지)을
 * 가져와 제휴 콘텐츠 생성의 근거로 쓴다. 인증은 Client ID/Secret 헤더. 제목의 {@code <b>} 태그·HTML
 * 엔티티는 정리해서 내려준다. (가격·이미지는 네이버 쇼핑 기준 — 쿠팡과 다를 수 있어 참고용)
 */
@Component
public class NaverShopClient {

    private final NaverSearchProperties props;
    private final RestClient http;

    public NaverShopClient(NaverSearchProperties props) {
        this.props = props;
        this.http = RestClient.builder().build();
    }

    public List<NaverProduct> searchShop(String query, int display) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        if (!props.configured()) {
            throw new NaverException("네이버 검색 API 키가 설정되지 않았어요. (서버에 NAVER_CLIENT_ID/SECRET 필요)");
        }
        URI uri = URI.create(props.searchBaseUrlOrDefault() + "/v1/search/shop.json"
                + "?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&display=" + display + "&sort=sim");
        try {
            NaverShopResponse res = http.get().uri(uri)
                    .header("X-Naver-Client-Id", props.clientId())
                    .header("X-Naver-Client-Secret", props.clientSecret())
                    .retrieve().body(NaverShopResponse.class);
            if (res == null || res.items() == null) {
                return List.of();
            }
            return res.items().stream().map(NaverShopClient::toProduct).toList();
        } catch (RestClientResponseException e) {
            throw new NaverException("네이버 상품 검색에 실패했어요. (" + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new NaverException("네이버 상품 검색에 실패했어요.", e);
        }
    }

    private static NaverProduct toProduct(Item it) {
        String cat = String.join(" > ", java.util.stream.Stream.of(
                it.category1(), it.category2(), it.category3(), it.category4())
                .filter(s -> s != null && !s.isBlank()).toList());
        Integer price = null;
        try {
            if (it.lprice() != null && !it.lprice().isBlank()) {
                price = Integer.parseInt(it.lprice().trim());
            }
        } catch (NumberFormatException ignored) {
            // 가격 파싱 실패는 무시(참고값)
        }
        return new NaverProduct(
                clean(it.title()), it.brand(), it.maker(), cat, price,
                it.image(), it.link(), it.mallName(), it.productId());
    }

    /** 제목의 <b> 태그·기본 HTML 엔티티 제거. */
    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        return s.replaceAll("<[^>]+>", "")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").trim();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NaverShopResponse(List<Item> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String title, String link, String image, String lprice,
            @JsonProperty("mallName") String mallName,
            @JsonProperty("productId") String productId,
            String brand, String maker,
            String category1, String category2, String category3, String category4) {
    }
}
