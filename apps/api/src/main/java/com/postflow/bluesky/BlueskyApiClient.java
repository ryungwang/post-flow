package com.postflow.bluesky;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thin client over the AT Protocol XRPC API (bsky.social by default).
 * MVP surface: session (create/refresh) + text post creation. Auth uses the session
 * accessJwt; expired tokens surface as {@link BlueskyAuthException} so the publisher
 * can refresh and retry.
 */
@Component
public class BlueskyApiClient {

    private static final String POST_COLLECTION = "app.bsky.feed.post";

    private final RestClient http;

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

    /** Create a text post; returns the record uri (at://…). 401/ExpiredToken → BlueskyAuthException. */
    public String createTextPost(String did, String accessJwt, String text) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("$type", POST_COLLECTION);
        record.put("text", text);
        record.put("createdAt", Instant.now().toString());
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
            if (e.getStatusCode().value() == 401
                    || (responseBody != null && responseBody.contains("ExpiredToken"))) {
                throw new BlueskyAuthException("블루스카이 액세스 토큰 만료");
            }
            throw new BlueskyApiException("블루스카이 발행 실패: " + shortError(responseBody));
        } catch (RestClientException e) {
            throw new BlueskyApiException("블루스카이 발행에 실패했어요(네트워크).", e);
        }
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
