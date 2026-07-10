package com.postflow.linkedin;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin client over LinkedIn's OAuth2 + versioned REST API.
 * Surface: authorization-code / refresh-token exchange, OpenID {@code userinfo}, and
 * post create/delete on the versioned {@code /rest/posts} API. Expired tokens surface as
 * {@link LinkedInAuthException} so the publisher can refresh and retry.
 *
 * <p>Images are published via the versioned Images API: {@code initializeUpload} → PUT the
 * bytes to the returned upload URL → attach the image URN to the post's {@code content.media}.
 */
@Component
public class LinkedInApiClient {

    private final LinkedInProperties properties;
    private final RestClient oauth;   // www.linkedin.com (token exchange)
    private final RestClient api;     // api.linkedin.com (userinfo + rest)
    private final RestClient media = RestClient.create(); // arbitrary media URLs (download + upload PUT)

    public LinkedInApiClient(LinkedInProperties properties) {
        this.properties = properties;
        this.oauth = RestClient.builder().baseUrl(properties.authorizeBaseUrlOrDefault()).build();
        this.api = RestClient.builder().baseUrl(properties.apiBaseUrlOrDefault()).build();
    }

    /** Exchange an authorization code for an access token (+ refresh token if approved). */
    public TokenResponse exchangeCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", properties.redirectUri());
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        return postToken(form, "링크드인 인증 코드 교환에 실패했어요.");
    }

    /** Exchange a refresh token for a fresh access token (approved apps only). */
    public TokenResponse refreshToken(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        return postToken(form, "링크드인 토큰 갱신에 실패했어요.");
    }

    private TokenResponse postToken(MultiValueMap<String, String> form, String errorMessage) {
        try {
            return oauth.post().uri("/oauth/v2/accessToken")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (RestClientResponseException e) {
            throw new LinkedInApiException(errorMessage + " (" + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new LinkedInApiException(errorMessage, e);
        }
    }

    /** OpenID Connect userinfo — {@code sub} is the member id (bare person id). */
    public UserInfo userInfo(String accessToken) {
        try {
            return api.get().uri("/v2/userinfo")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(UserInfo.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                throw new LinkedInAuthException("링크드인 토큰이 만료됐어요.");
            }
            throw new LinkedInApiException("링크드인 프로필 조회에 실패했어요.", e);
        } catch (RestClientException e) {
            throw new LinkedInApiException("링크드인 프로필 조회에 실패했어요.", e);
        }
    }

    /**
     * Publish a post to the member's feed via {@code /rest/posts} — text, plus an optional image
     * when {@code mediaUrl} points to an image (uploaded via the Images API and attached).
     * {@code memberId} is the bare person id (from {@code userinfo.sub}). Returns the post URN
     * (from the {@code x-restli-id} response header).
     */
    public String createPost(String memberId, String accessToken, String text, String mediaUrl) {
        Map<String, Object> distribution = new LinkedHashMap<>();
        distribution.put("feedDistribution", "MAIN_FEED");
        distribution.put("targetEntities", List.of());
        distribution.put("thirdPartyDistributionChannels", List.of());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("author", "urn:li:person:" + memberId);
        body.put("commentary", escapeCommentary(text == null ? "" : text));
        body.put("visibility", "PUBLIC");
        body.put("distribution", distribution);
        if (isImage(mediaUrl)) {
            String imageUrn = uploadImage(memberId, accessToken, mediaUrl);
            if (imageUrn != null) {
                body.put("content", Map.of("media", Map.of("id", imageUrn, "altText", "")));
            }
        }
        body.put("lifecycleState", "PUBLISHED");
        body.put("isReshareDisabledByAuthor", false);

        try {
            var response = api.post().uri("/rest/posts")
                    .headers(this::restHeaders)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            String urn = response.getHeaders().getFirst("x-restli-id");
            if (urn == null || urn.isBlank()) {
                urn = response.getHeaders().getFirst("x-linkedin-id");
            }
            if (urn == null || urn.isBlank()) {
                throw new LinkedInApiException("링크드인 발행 응답에 게시물 식별자가 없어요.");
            }
            return urn;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                throw new LinkedInAuthException("링크드인 토큰이 만료됐어요.");
            }
            throw new LinkedInApiException("링크드인 발행에 실패했어요. (" + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new LinkedInApiException("링크드인 발행에 실패했어요.", e);
        }
    }

    /** Delete a previously published post. {@code postUrn} is what {@link #createPost} returned. */
    public void deletePost(String postUrn, String accessToken) {
        String encoded = URLEncoder.encode(postUrn, StandardCharsets.UTF_8);
        try {
            api.delete().uri("/rest/posts/{urn}", encoded)
                    .headers(this::restHeaders)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            if (status.value() == 401) {
                throw new LinkedInAuthException("링크드인 토큰이 만료됐어요.");
            }
            if (status.value() == 404) {
                return; // already gone — idempotent
            }
            throw new LinkedInApiException("링크드인 게시물 삭제에 실패했어요. (" + status.value() + ")", e);
        } catch (RestClientException e) {
            throw new LinkedInApiException("링크드인 게시물 삭제에 실패했어요.", e);
        }
    }

    /**
     * Upload an image via the versioned Images API and return its URN
     * ({@code urn:li:image:…}) for attaching to a post. Three steps: initialize the upload
     * (get an upload URL + image urn), download the source bytes, PUT them to the upload URL.
     * Returns null if the image can't be fetched.
     */
    private String uploadImage(String memberId, String accessToken, String mediaUrl) {
        // 1) initialize upload
        InitUploadResponse init;
        try {
            init = api.post().uri("/rest/images?action=initializeUpload")
                    .headers(this::restHeaders)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("initializeUploadRequest", Map.of("owner", "urn:li:person:" + memberId)))
                    .retrieve()
                    .body(InitUploadResponse.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                throw new LinkedInAuthException("링크드인 토큰이 만료됐어요.");
            }
            throw new LinkedInApiException("링크드인 이미지 업로드 준비에 실패했어요. (" + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new LinkedInApiException("링크드인 이미지 업로드 준비에 실패했어요.", e);
        }
        if (init == null || init.value() == null
                || init.value().uploadUrl() == null || init.value().image() == null) {
            throw new LinkedInApiException("링크드인 이미지 업로드 URL을 받지 못했어요.");
        }

        // 2) download source bytes
        byte[] bytes;
        try {
            bytes = media.get().uri(URI.create(mediaUrl))
                    .header(HttpHeaders.USER_AGENT, "PostFlow/1.0 (+https://postflow.synub.io)")
                    .retrieve().body(byte[].class);
        } catch (RestClientException e) {
            throw new LinkedInApiException("이미지를 불러오지 못했어요(URL 접근 불가).", e);
        }
        if (bytes == null || bytes.length == 0) {
            return null;
        }

        // 3) PUT bytes to the upload URL (absolute; separate media host)
        try {
            media.put().uri(URI.create(init.value().uploadUrl()))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.parseMediaType(contentTypeFor(mediaUrl)))
                    .body(bytes)
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw new LinkedInApiException("링크드인 이미지 업로드에 실패했어요. (" + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new LinkedInApiException("링크드인 이미지 업로드에 실패했어요.", e);
        }
        return init.value().image();
    }

    private void restHeaders(HttpHeaders headers) {
        headers.add("LinkedIn-Version", properties.restliVersionOrDefault());
        headers.add("X-Restli-Protocol-Version", "2.0.0");
    }

    private static boolean isImage(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String u = stripQuery(url).toLowerCase();
        return u.endsWith(".jpg") || u.endsWith(".jpeg") || u.endsWith(".png")
                || u.endsWith(".webp") || u.endsWith(".gif");
    }

    private static String contentTypeFor(String url) {
        String u = stripQuery(url).toLowerCase();
        if (u.endsWith(".png")) return "image/png";
        if (u.endsWith(".webp")) return "image/webp";
        if (u.endsWith(".gif")) return "image/gif";
        return "image/jpeg";
    }

    private static String stripQuery(String url) {
        int q = url.indexOf('?');
        return q >= 0 ? url.substring(0, q) : url;
    }

    record InitUploadResponse(Value value) {
        record Value(String uploadUrl, String image) {
        }
    }

    /**
     * The {@code commentary} field uses LinkedIn "Little Text" — these reserved characters
     * must be backslash-escaped or the post is rejected / renders wrong.
     */
    static String escapeCommentary(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ("\\|{}@[]()<>#*_~".indexOf(c) >= 0) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("refresh_token_expires_in") Long refreshTokenExpiresIn,
            String scope) {
    }

    public record UserInfo(
            String sub,
            String name,
            @JsonProperty("given_name") String givenName,
            @JsonProperty("family_name") String familyName,
            String picture,
            String email) {
    }
}
