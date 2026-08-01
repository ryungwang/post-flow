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
    // 공개 데이터(프로필·게시물) 조회는 인증 불필요한 공개 AppView 사용.
    private final RestClient appView = RestClient.builder().baseUrl("https://public.api.bsky.app").build();

    public BlueskyApiClient(BlueskyProperties properties) {
        this.http = RestClient.builder().baseUrl(properties.baseUrlOrDefault()).build();
    }

    /** 공개 프로필(팔로워·게시물 수 등). actor = handle 또는 DID. */
    public BskyProfile getProfile(String actor) {
        try {
            return appView.get().uri("/xrpc/app.bsky.actor.getProfile?actor={a}", actor)
                    .retrieve().body(BskyProfile.class);
        } catch (RestClientException e) {
            throw new BlueskyApiException("블루스카이 프로필 조회에 실패했어요.", e);
        }
    }

    /** 작성자 피드(내 게시물). 리포스트/답글 제외한 본인 게시물 위주. */
    public BskyFeed getAuthorFeed(String actor, int limit) {
        try {
            return appView.get()
                    .uri("/xrpc/app.bsky.feed.getAuthorFeed?actor={a}&limit={l}&filter=posts_no_replies", actor, limit)
                    .retrieve().body(BskyFeed.class);
        } catch (RestClientException e) {
            throw new BlueskyApiException("블루스카이 게시물 조회에 실패했어요.", e);
        }
    }

    public record BskyProfile(String handle, String displayName, String avatar,
                              Integer followersCount, Integer postsCount, String description) {
    }

    public record BskyFeed(List<BskyFeedItem> feed, String cursor) {
    }

    public record BskyFeedItem(BskyPost post) {
    }

    public record BskyPost(String uri, BskyRecord record, Integer likeCount,
                           Integer repostCount, Integer replyCount, BskyEmbed embed) {
    }

    public record BskyRecord(String text, String createdAt) {
    }

    public record BskyEmbed(List<BskyImage> images) {
    }

    public record BskyImage(String thumb, String fullsize) {
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

    /** Delete a post record by rkey (last segment of the at:// uri). 401/ExpiredToken → BlueskyAuthException. */
    public void deleteRecord(String did, String accessJwt, String rkey) {
        try {
            http.post().uri("/xrpc/com.atproto.repo.deleteRecord")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("repo", did, "collection", POST_COLLECTION, "rkey", rkey))
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            String responseBody = e.getResponseBodyAsString();
            if (isAuthError(e, responseBody)) {
                throw new BlueskyAuthException("블루스카이 액세스 토큰 만료");
            }
            throw new BlueskyApiException("블루스카이 삭제 실패: " + shortError(responseBody));
        } catch (RestClientException e) {
            throw new BlueskyApiException("블루스카이 삭제에 실패했어요(네트워크).", e);
        }
    }

    /**
     * 내 게시물(또는 특정 댓글)에 답글을 단다. root/parent 강한참조(uri+cid)가 필요해서 대상의 cid를
     * 공개 스레드 조회로 먼저 해석한다. {@code parentUri}가 원글이면 첫 댓글(최상위 댓글)이 되고,
     * 댓글의 uri면 그 댓글에 대한 답글이 된다. Returns the reply record uri. 401 → BlueskyAuthException.
     */
    public String createReply(String did, String accessJwt, String text, String rootUri, String parentUri) {
        String rootCid = getRecordCid(accessJwt, rootUri);
        String parentCid = rootUri.equals(parentUri) ? rootCid : getRecordCid(accessJwt, parentUri);
        if (rootCid == null || parentCid == null) {
            throw new BlueskyApiException("답글 대상 게시물을 찾지 못했어요.");
        }
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("$type", POST_COLLECTION);
        record.put("text", text);
        record.put("createdAt", Instant.now().toString());
        record.put("reply", Map.of(
                "root", Map.of("uri", rootUri, "cid", rootCid),
                "parent", Map.of("uri", parentUri, "cid", parentCid)));
        Map<String, Object> body = Map.of("repo", did, "collection", POST_COLLECTION, "record", record);
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
            throw new BlueskyApiException("블루스카이 답글 실패: " + shortError(responseBody));
        } catch (RestClientException e) {
            throw new BlueskyApiException("블루스카이 답글에 실패했어요(네트워크).", e);
        }
    }

    /** 게시물에 달린 직접 답글(댓글) 목록 — 자동응답 매칭용. 공개 AppView 사용(인증 불필요). */
    public List<PostThreadView> getReplies(String postUri) {
        try {
            PostThreadResponse res = appView.get()
                    .uri("/xrpc/app.bsky.feed.getPostThread?uri={u}&depth=1", postUri)
                    .retrieve().body(PostThreadResponse.class);
            if (res == null || res.thread() == null || res.thread().replies() == null) {
                return List.of();
            }
            return res.thread().replies();
        } catch (RestClientException e) {
            throw new BlueskyApiException("블루스카이 댓글 조회에 실패했어요.", e);
        }
    }

    /**
     * 강한참조용 cid를 PDS의 getRecord로 즉시 해석한다(공개 AppView 색인 지연 없이 발행 직후에도 확실).
     * at:// uri에 소유자 repo(did)와 rkey가 들어 있어 그걸로 조회한다. 401 → BlueskyAuthException.
     */
    private String getRecordCid(String accessJwt, String atUri) {
        String repo = repoOf(atUri);
        String rkey = rkeyOf(atUri);
        if (repo == null || rkey == null) {
            return null;
        }
        try {
            GetRecordResponse res = http.get()
                    .uri("/xrpc/com.atproto.repo.getRecord?repo={r}&collection={c}&rkey={k}",
                            repo, POST_COLLECTION, rkey)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessJwt)
                    .retrieve().body(GetRecordResponse.class);
            return res != null ? res.cid() : null;
        } catch (RestClientResponseException e) {
            String body = e.getResponseBodyAsString();
            if (isAuthError(e, body)) {
                throw new BlueskyAuthException("블루스카이 액세스 토큰 만료");
            }
            throw new BlueskyApiException("블루스카이 게시물 조회 실패: " + shortError(body));
        } catch (RestClientException e) {
            throw new BlueskyApiException("블루스카이 게시물 조회 실패(네트워크).", e);
        }
    }

    /** at://{did}/{collection}/{rkey} 에서 소유자 repo(did) 추출. */
    private static String repoOf(String atUri) {
        if (atUri == null || !atUri.startsWith("at://")) {
            return null;
        }
        String rest = atUri.substring("at://".length());
        int slash = rest.indexOf('/');
        return slash > 0 ? rest.substring(0, slash) : rest;
    }

    /** at:// uri의 마지막 세그먼트(rkey) 추출. */
    private static String rkeyOf(String atUri) {
        if (atUri == null) {
            return null;
        }
        int slash = atUri.lastIndexOf('/');
        return slash >= 0 ? atUri.substring(slash + 1) : atUri;
    }

    private record GetRecordResponse(String uri, String cid) {
    }

    public record PostThreadResponse(PostThreadView thread) {
    }

    public record PostThreadView(ThreadPost post, List<PostThreadView> replies) {
    }

    public record ThreadPost(String uri, String cid, BskyRecord record, ThreadAuthor author) {
    }

    public record ThreadAuthor(String handle, String displayName) {
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
