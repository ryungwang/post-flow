package com.postflow.facebook;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * Thin client over the Facebook Graph API for Page publishing. Flow: exchange the OAuth code
 * for a user token → list the Pages the user manages (each with its own Page access token) →
 * publish to a Page's feed (or /photos for an image, which Facebook fetches by URL) → delete.
 * Invalid tokens (OAuthException / code 190) surface as {@link FacebookAuthException}.
 */
@Component
public class FacebookApiClient {

    private final FacebookProperties properties;
    private final RestClient graph;

    public FacebookApiClient(FacebookProperties properties) {
        this.properties = properties;
        this.graph = RestClient.builder().baseUrl(properties.graphBaseUrlOrDefault()).build();
    }

    private String ver() {
        return "/" + properties.apiVersionOrDefault();
    }

    /** Exchange an authorization code for a user access token. */
    public FbToken exchangeCode(String code) {
        String uri = UriComponentsBuilder.fromPath(ver() + "/oauth/access_token")
                .queryParam("client_id", properties.appId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("client_secret", properties.appSecret())
                .queryParam("code", code)
                .build().toUriString();
        try {
            return graph.get().uri(uri).retrieve().body(FbToken.class);
        } catch (RestClientResponseException e) {
            throw new FacebookApiException("페이스북 인증 코드 교환에 실패했어요. (" + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new FacebookApiException("페이스북 인증 코드 교환에 실패했어요.", e);
        }
    }

    /** List the Pages this user manages — each entry carries its own Page access token. */
    public List<FbPage> getPages(String userToken) {
        String uri = UriComponentsBuilder.fromPath(ver() + "/me/accounts")
                .queryParam("fields", "id,name,access_token,picture{url}")
                .queryParam("access_token", userToken)
                .build().toUriString();
        try {
            FbPagesResponse res = graph.get().uri(uri).retrieve().body(FbPagesResponse.class);
            return res != null && res.data() != null ? res.data() : List.of();
        } catch (RestClientResponseException e) {
            if (isAuthError(e)) {
                throw new FacebookAuthException("페이스북 토큰이 유효하지 않아요.");
            }
            throw new FacebookApiException("페이스북 페이지 목록 조회에 실패했어요. (" + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new FacebookApiException("페이스북 페이지 목록 조회에 실패했어요.", e);
        }
    }

    /**
     * Publish to a Page — text to /feed, or an image via /photos (Facebook fetches {@code url}).
     * Returns the post id. 190/OAuthException → {@link FacebookAuthException}.
     */
    public String createPost(String pageId, String pageToken, String text, String mediaUrl) {
        boolean image = isImage(mediaUrl);
        String path = ver() + "/" + pageId + (image ? "/photos" : "/feed");
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("access_token", pageToken);
        if (image) {
            form.add("url", mediaUrl);
            if (text != null && !text.isBlank()) {
                form.add("caption", text);
            }
        } else {
            form.add("message", text == null ? "" : text);
        }
        try {
            FbPostResponse res = graph.post().uri(path)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve().body(FbPostResponse.class);
            if (res == null) {
                throw new FacebookApiException("페이스북 발행 응답이 비어 있어요.");
            }
            // /photos returns {id, post_id}; /feed returns {id}
            return res.postId() != null ? res.postId() : res.id();
        } catch (RestClientResponseException e) {
            if (isAuthError(e)) {
                throw new FacebookAuthException("페이스북 토큰이 만료됐어요.");
            }
            throw new FacebookApiException("페이스북 발행에 실패했어요. (" + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new FacebookApiException("페이스북 발행에 실패했어요.", e);
        }
    }

    /**
     * Top-level comments on a Page post. {@code filter=toplevel} keeps replies-to-replies out,
     * so an auto-reply doesn't fire on a nested thread. Requires {@code pages_read_user_content}.
     * A deleted post (404) has no comments rather than being an error.
     */
    public List<FbComment> getComments(String postId, String pageToken) {
        String uri = UriComponentsBuilder.fromPath(ver() + "/" + postId + "/comments")
                .queryParam("fields", "id,message,from{name}")
                .queryParam("filter", "toplevel")
                .queryParam("limit", 50)
                .queryParam("access_token", pageToken)
                .build().toUriString();
        try {
            FbCommentsResponse res = graph.get().uri(uri).retrieve().body(FbCommentsResponse.class);
            return res == null || res.data() == null ? List.of() : res.data();
        } catch (RestClientResponseException e) {
            if (isAuthError(e)) {
                throw new FacebookAuthException("페이스북 토큰이 만료됐어요.");
            }
            if (e.getStatusCode().value() == 404) {
                return List.of();
            }
            throw new FacebookApiException(
                    "페이스북 댓글을 불러오지 못했어요. (" + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new FacebookApiException("페이스북 댓글을 불러오지 못했어요.", e);
        }
    }

    /**
     * Reply to a comment. Posting to {@code /{commentId}/comments} nests the reply under that
     * comment (so the commenter is notified), unlike posting to the post itself.
     * Requires {@code pages_manage_engagement}.
     */
    public String replyToComment(String commentId, String pageToken, String text) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("access_token", pageToken);
        form.add("message", text == null ? "" : text);
        try {
            FbPostResponse res = graph.post().uri(ver() + "/" + commentId + "/comments")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve().body(FbPostResponse.class);
            return res == null ? null : res.id();
        } catch (RestClientResponseException e) {
            if (isAuthError(e)) {
                throw new FacebookAuthException("페이스북 토큰이 만료됐어요.");
            }
            throw new FacebookApiException(
                    "페이스북 답글 작성에 실패했어요. (" + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new FacebookApiException("페이스북 답글 작성에 실패했어요.", e);
        }
    }

    /** Delete a Page post/photo by object id. Missing objects are treated as already gone. */
    public void deletePost(String objectId, String pageToken) {
        String uri = UriComponentsBuilder.fromPath(ver() + "/" + objectId)
                .queryParam("access_token", pageToken)
                .build().toUriString();
        try {
            graph.delete().uri(uri).retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (isAuthError(e)) {
                throw new FacebookAuthException("페이스북 토큰이 만료됐어요.");
            }
            String body = e.getResponseBodyAsString();
            if (e.getStatusCode().value() == 404 || body.contains("does not exist")) {
                return; // already gone
            }
            throw new FacebookApiException("페이스북 게시물 삭제에 실패했어요. (" + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new FacebookApiException("페이스북 게시물 삭제에 실패했어요.", e);
        }
    }

    /** Graph auth errors are OAuthException / code 190 (invalid or expired token). */
    private static boolean isAuthError(RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        return body.contains("\"code\":190") || body.contains("OAuthException");
    }

    private static boolean isImage(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String u = url;
        int q = u.indexOf('?');
        if (q >= 0) {
            u = u.substring(0, q);
        }
        u = u.toLowerCase();
        return u.endsWith(".jpg") || u.endsWith(".jpeg") || u.endsWith(".png")
                || u.endsWith(".webp") || u.endsWith(".gif");
    }

    public record FbToken(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") Long expiresIn) {
    }

    public record FbPagesResponse(List<FbPage> data) {
    }

    public record FbPage(
            String id,
            String name,
            @JsonProperty("access_token") String accessToken,
            Picture picture) {
        public String pictureUrl() {
            return picture != null && picture.data() != null ? picture.data().url() : null;
        }

        public record Picture(Data data) {
            public record Data(String url) {
            }
        }
    }

    public record FbCommentsResponse(List<FbComment> data) {
    }

    /** A comment on a Page post. {@code from} is absent unless the app has the right permissions. */
    public record FbComment(String id, String message, From from) {
        public String authorName() {
            return from == null ? null : from.name();
        }

        public record From(String id, String name) {
        }
    }

    public record FbPostResponse(String id, @JsonProperty("post_id") String postId) {
    }
}
