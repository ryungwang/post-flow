package com.postflow.instagram;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * "Instagram API with Instagram login" OAuth 클라이언트 — 페이스북 페이지 없이 IG 계정에 직접
 * 로그인해 토큰을 얻는다. 흐름: 인가코드 → 단기토큰(api.instagram.com) → 장기토큰(graph.instagram.com)
 * → 프로필(/me). 여기서 얻은 토큰·IG user id는 {@link InstagramApiClient} 가 graph.instagram.com 으로
 * 발행·댓글·인사이트를 호출하는 데 쓰인다(페북 graph 아님).
 */
@Component
public class InstagramLoginClient {

    private final InstagramProperties props;
    private final RestClient http;

    public InstagramLoginClient(InstagramProperties props) {
        this.props = props;
        this.http = RestClient.builder().build();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** 인가코드 → 단기 액세스 토큰(+ IG user id). POST api.instagram.com/oauth/access_token (form). */
    public IgLoginToken exchangeCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", props.appId());
        form.add("client_secret", props.appSecret());
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", props.redirectUri());
        form.add("code", code);
        try {
            return http.post()
                    .uri(URI.create(props.apiBaseUrlOrDefault() + "/oauth/access_token"))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve().body(IgLoginToken.class);
        } catch (RestClientResponseException e) {
            throw new InstagramApiException(
                    "인스타그램 인증 코드 교환에 실패했어요. (" + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new InstagramApiException("인스타그램 인증 코드 교환에 실패했어요.", e);
        }
    }

    /** 단기 → 장기(60일) 토큰. GET graph.instagram.com/access_token?grant_type=ig_exchange_token. */
    public IgLongLived exchangeForLongLived(String shortToken) {
        URI uri = URI.create(props.graphBaseUrlOrDefault() + "/access_token"
                + "?grant_type=ig_exchange_token"
                + "&client_secret=" + enc(props.appSecret())
                + "&access_token=" + enc(shortToken));
        try {
            return http.get().uri(uri).retrieve().body(IgLongLived.class);
        } catch (RestClientResponseException e) {
            throw new InstagramApiException(
                    "인스타그램 장기 토큰 교환에 실패했어요. (" + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new InstagramApiException("인스타그램 장기 토큰 교환에 실패했어요.", e);
        }
    }

    /** 연결 계정 프로필. GET graph.instagram.com/{ver}/me?fields=user_id,username,account_type,... */
    public IgLoginProfile getProfile(String token) {
        URI uri = URI.create(props.graphBaseUrlOrDefault() + "/" + props.apiVersionOrDefault() + "/me"
                + "?fields=" + enc("user_id,username,account_type,profile_picture_url")
                + "&access_token=" + enc(token));
        try {
            return http.get().uri(uri).retrieve().body(IgLoginProfile.class);
        } catch (RestClientResponseException e) {
            throw new InstagramApiException(
                    "인스타그램 프로필 조회에 실패했어요. (" + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new InstagramApiException("인스타그램 프로필 조회에 실패했어요.", e);
        }
    }

    /** 단기 토큰 교환 응답. user_id = 앱 스코프 IG 계정 id. */
    public record IgLoginToken(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("user_id") String userId,
            String permissions) {
    }

    /** 장기 토큰 교환 응답. expires_in = 초(≈60일). */
    public record IgLongLived(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") Long expiresIn) {
    }

    /** /me 응답. account_type ∈ BUSINESS / MEDIA_CREATOR / PERSONAL(발행 불가). */
    public record IgLoginProfile(
            @JsonProperty("user_id") String userId,
            String id,
            String username,
            @JsonProperty("account_type") String accountType,
            @JsonProperty("profile_picture_url") String profilePictureUrl) {
        /** user_id(신규) 우선, 없으면 id(구필드). */
        public String accountId() {
            return userId != null ? userId : id;
        }

        public boolean canPublish() {
            return "BUSINESS".equalsIgnoreCase(accountType) || "MEDIA_CREATOR".equalsIgnoreCase(accountType);
        }
    }
}
