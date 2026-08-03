package com.postflow.coupang;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 쿠팡 파트너스 Open API 클라이언트. 인증은 CEA HMAC-SHA256 서명:
 * {@code message = datetime(yyMMdd'T'HHmmss'Z', GMT) + METHOD + path + query}, 서명은 secretKey로 HMAC 후 hex.
 * Authorization 헤더 = {@code CEA algorithm=HmacSHA256, access-key=..., signed-date=..., signature=...}.
 * 우선 딥링크 생성(상품 URL → link.coupang.com 제휴 링크)만 제공한다.
 */
@Component
public class CoupangPartnersClient {

    private static final String DEEPLINK_PATH = "/v2/providers/affiliate_open_api/apis/openapi/v1/deeplink";
    private static final String GOLDBOX_PATH = "/v2/providers/affiliate_open_api/apis/openapi/v1/products/goldbox";
    private static final String BEST_PATH = "/v2/providers/affiliate_open_api/apis/openapi/products/bestcategories/";
    private static final String SEARCH_PATH = "/v2/providers/affiliate_open_api/apis/openapi/products/search";
    private static final DateTimeFormatter SIGNED_DATE =
            DateTimeFormatter.ofPattern("yyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final CoupangProperties properties;
    private final RestClient http;

    public CoupangPartnersClient(CoupangProperties properties) {
        this.properties = properties;
        this.http = RestClient.builder().baseUrl(properties.baseUrlOrDefault()).build();
    }

    public boolean configured() {
        return properties.configured();
    }

    /**
     * 쿠팡 상품 URL을 제휴 딥링크(link.coupang.com/a/...)로 변환. subId를 주면 그 채널로 실적이 잡히는 링크,
     * null이면 base 딥링크(앱이 플랫폼별로 subId를 덧붙인다).
     */
    public String deeplink(String coupangUrl, String subId) {
        if (!properties.configured()) {
            throw new CoupangApiException("쿠팡 Open API 키가 설정되지 않았어요. (COUPANG_ACCESS_KEY/COUPANG_SECRET_KEY 필요)");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("coupangUrls", List.of(coupangUrl));
        if (subId != null && !subId.isBlank()) {
            body.put("subId", subId);
        }
        try {
            DeeplinkResponse res = http.post().uri(DEEPLINK_PATH)
                    .header(HttpHeaders.AUTHORIZATION, authorization("POST", DEEPLINK_PATH, ""))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve().body(DeeplinkResponse.class);
            if (res == null || res.data() == null || res.data().isEmpty()) {
                throw new CoupangApiException("딥링크 응답이 비었어요. 유효한 쿠팡 상품 URL인지 확인해 주세요.");
            }
            String shorten = res.data().get(0).shortenUrl();
            if (shorten == null || shorten.isBlank()) {
                throw new CoupangApiException("딥링크가 생성되지 않았어요.");
            }
            return shorten;
        } catch (RestClientResponseException e) {
            throw new CoupangApiException("쿠팡 딥링크 생성 실패(" + e.getStatusCode().value() + "): "
                    + shortError(e.getResponseBodyAsString()));
        } catch (RestClientException e) {
            throw new CoupangApiException("쿠팡 딥링크 생성에 실패했어요(네트워크).", e);
        }
    }

    /** 오늘의 골드박스(특가) 상품 — "지금 미는 딜" 소싱. productUrl은 이미 제휴 링크. */
    public List<CoupangProduct> goldbox() {
        ListResponse res = get(GOLDBOX_PATH, "", ListResponse.class);
        return res != null && res.data() != null ? res.data() : List.of();
    }

    /** 카테고리 베스트 상품(잘 팔리는 순위). categoryId = 쿠팡 고정 카테고리 코드(예: 1010 가전디지털). */
    public List<CoupangProduct> bestCategories(String categoryId, int limit) {
        String path = BEST_PATH + categoryId;
        String query = "limit=" + clampLimit(limit);
        ListResponse res = get(path, query, ListResponse.class);
        return res != null && res.data() != null ? res.data() : List.of();
    }

    /** 키워드 상품 검색. 검색 응답은 data.productData 안에 상품 배열이 들어온다. */
    public List<CoupangProduct> search(String keyword, int limit) {
        String query = "keyword=" + urlEncode(keyword) + "&limit=" + clampLimit(limit);
        SearchResponse res = get(SEARCH_PATH, query, SearchResponse.class);
        return res != null && res.data() != null && res.data().productData() != null
                ? res.data().productData() : List.of();
    }

    /** 공통 GET — 서명(path+query)과 실제 요청 URL의 query를 동일하게 맞춰 보낸다. */
    private <T> T get(String path, String query, Class<T> type) {
        if (!properties.configured()) {
            throw new CoupangApiException("쿠팡 Open API 키가 설정되지 않았어요. (COUPANG_ACCESS_KEY/COUPANG_SECRET_KEY 필요)");
        }
        String uri = properties.baseUrlOrDefault() + path + (query.isBlank() ? "" : "?" + query);
        try {
            return http.get().uri(URI.create(uri))
                    .header(HttpHeaders.AUTHORIZATION, authorization("GET", path, query))
                    .retrieve().body(type);
        } catch (RestClientResponseException e) {
            throw new CoupangApiException("쿠팡 상품 조회 실패(" + e.getStatusCode().value() + "): "
                    + shortError(e.getResponseBodyAsString()));
        } catch (RestClientException e) {
            throw new CoupangApiException("쿠팡 상품 조회에 실패했어요(네트워크).", e);
        }
    }

    private static int clampLimit(int limit) {
        return Math.max(1, Math.min(50, limit));
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    /** CEA HMAC 서명 Authorization 헤더. */
    private String authorization(String method, String path, String query) {
        String datetime = SIGNED_DATE.format(Instant.now());
        String message = datetime + method + path + query;
        String signature = hmacSha256Hex(properties.secretKey(), message);
        return "CEA algorithm=HmacSHA256, access-key=" + properties.accessKey()
                + ", signed-date=" + datetime + ", signature=" + signature;
    }

    private static String hmacSha256Hex(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new CoupangApiException("쿠팡 서명 생성에 실패했어요.", e);
        }
    }

    private static String shortError(String body) {
        if (body == null || body.isBlank()) {
            return "unknown";
        }
        return body.length() > 200 ? body.substring(0, 200) : body;
    }

    private record DeeplinkResponse(String rCode, String rMessage, List<DeeplinkData> data) {
    }

    private record DeeplinkData(String originalUrl, String shortenUrl, String landingUrl) {
    }

    /** 골드박스/베스트 응답 — data가 상품 배열. */
    private record ListResponse(String rCode, String rMessage, List<CoupangProduct> data) {
    }

    /** 검색 응답 — data.productData가 상품 배열. */
    private record SearchResponse(String rCode, String rMessage, SearchData data) {
    }

    private record SearchData(String landingUrl, List<CoupangProduct> productData) {
    }

    /** 쿠팡 상품(소싱용). productUrl은 승인 파트너 기준 이미 제휴 딥링크. 알 수 없는 필드는 무시. */
    public record CoupangProduct(
            Long productId,
            String productName,
            Integer productPrice,
            String productImage,
            String productUrl,
            String categoryName,
            Boolean isRocket,
            Boolean isFreeShipping
    ) {
    }
}
