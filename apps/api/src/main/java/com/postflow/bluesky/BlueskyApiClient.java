package com.postflow.bluesky;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin client over the AT Protocol XRPC API (bsky.social by default).
 * Surface: session (create/refresh) + post creation (text + optional image). Auth uses the
 * session accessJwt; expired tokens surface as {@link BlueskyAuthException} so the publisher
 * can refresh and retry. Images are uploaded as blobs and embedded (Bluesky doesn't fetch by URL).
 */
@Component
public class BlueskyApiClient {

    private static final String POST_COLLECTION = "app.bsky.feed.post";

    private final RestClient http;
    private final RestClient downloader = RestClient.create(); // arbitrary media URLs

    public BlueskyApiClient(BlueskyProperties properties) {
        this.http = RestClient.builder().baseUrl(properties.baseUrlOrDefault()).build();
    }

    /** Exchange handle + app password for a session. Bad credentials → BlueskyApiException. */
    public BlueskySession createSession(String identifier, String appPassword) {
        try {
            return http.post().uri("/xrpc/com.atproto.server.createSession")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("identifier", identifier, "password", appPassword))
                    .retrieve().body(BlueskySession.class);
        } catch (RestClientResponseException e) {
            throw new BlueskyApiException("블루스카이 로그인에 실패했어요. 핸들과 앱 비밀번호를 확인해 주세요.");
        } catch (RestClientException e) {
            throw new BlueskyApiException("블루스카이 연결에 실패했어요. 잠시 후 다시 시도해 주세요.", e);
        }
    }

    /** Rotate the session using refreshJwt. Failure = reconnect required. */
    public BlueskySession refreshSession(String refreshJwt) {
        try {
            return http.post().uri("/xrpc/com.atproto.server.refreshSession")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshJwt)
                    .retrieve().body(BlueskySession.class);
        } catch (RestClientException e) {
            throw new BlueskyAuthException("블루스카이 세션 갱신 실패 — 재연결이 필요해요.");
        }
    }

    /**
     * Create a post (text + optional image). If {@code mediaUrl} is an image, its bytes are
     * uploaded as a blob and embedded. Non-image media (e.g. video) is ignored for now (text only).
     * Returns the record uri (at://…). 401/ExpiredToken → BlueskyAuthException.
     */
    public String createPost(String did, String accessJwt, String text, String mediaUrl) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("$type", POST_COLLECTION);
        record.put("text", text);
        record.put("createdAt", Instant.now().toString());
        if (isImage(mediaUrl)) {
            Object blob = uploadImageBlob(accessJwt, mediaUrl);
            if (blob != null) {
                record.put("embed", Map.of(
                        "$type", "app.bsky.embed.images",
                        "images", List.of(Map.of("alt", "", "image", blob))));
            }
        }
        Map<String, Object> body = Map.of(
                "repo", did, "collection", POST_COLLECTION, "record", record);
        try {
            CreateRecordResponse res = http.post().uri("/xrpc/com.atproto.repo.createRecord")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve().body(CreateRecordResponse.class);
            return res != null ? res.uri() : null;
        } catch (RestClientResponseException e) {
            String responseBody = e.getResponseBodyAsString();
            if (isAuthError(e, responseBody)) {
                throw new BlueskyAuthException("블루스카이 액세스 토큰 만료");
            }
            throw new BlueskyApiException("블루스카이 발행 실패: " + shortError(responseBody));
        } catch (RestClientException e) {
            throw new BlueskyApiException("블루스카이 발행에 실패했어요(네트워크).", e);
        }
    }

    /** Download the image and upload it as a blob; returns the blob JSON node for embedding. */
    @SuppressWarnings("unchecked")
    private Object uploadImageBlob(String accessJwt, String mediaUrl) {
        byte[] bytes;
        try {
            bytes = downloader.get().uri(URI.create(mediaUrl))
                    .header(HttpHeaders.USER_AGENT, "PostFlow/1.0 (+https://postflow.synub.io)")
                    .retrieve().body(byte[].class);
        } catch (RestClientException e) {
            throw new BlueskyApiException("이미지를 불러오지 못했어요(URL 접근 불가).", e);
        }
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            Map<String, Object> resp = http.post().uri("/xrpc/com.atproto.repo.uploadBlob")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessJwt)
                    .contentType(MediaType.parseMediaType(contentTypeFor(mediaUrl)))
                    .body(bytes)
                    .retrieve().body(Map.class);
            return resp != null ? resp.get("blob") : null;
        } catch (RestClientResponseException e) {
            String responseBody = e.getResponseBodyAsString();
            if (isAuthError(e, responseBody)) {
                throw new BlueskyAuthException("블루스카이 액세스 토큰 만료");
            }
            throw new BlueskyApiException("블루스카이 이미지 업로드 실패: " + shortError(responseBody));
        } catch (RestClientException e) {
            throw new BlueskyApiException("블루스카이 이미지 업로드 실패(네트워크).", e);
        }
    }

    private static boolean isImage(String url) {
        String u = stripQuery(url);
        return u != null && (u.endsWith(".jpg") || u.endsWith(".jpeg") || u.endsWith(".png")
                || u.endsWith(".webp") || u.endsWith(".gif"));
    }

    private static String contentTypeFor(String url) {
        String u = stripQuery(url);
        if (u == null) return "image/jpeg";
        if (u.endsWith(".png")) return "image/png";
        if (u.endsWith(".webp")) return "image/webp";
        if (u.endsWith(".gif")) return "image/gif";
        return "image/jpeg";
    }

    private static String stripQuery(String url) {
        if (url == null || url.isBlank()) return null;
        String u = url.toLowerCase();
        int q = u.indexOf('?');
        return q >= 0 ? u.substring(0, q) : u;
    }

    private static boolean isAuthError(RestClientResponseException e, String body) {
        return e.getStatusCode().value() == 401 || (body != null && body.contains("ExpiredToken"));
    }

    private static String shortError(String body) {
        if (body == null || body.isBlank()) {
            return "unknown";
        }
        return body.length() > 200 ? body.substring(0, 200) : body;
    }

    private record CreateRecordResponse(String uri, String cid) {
    }
}
