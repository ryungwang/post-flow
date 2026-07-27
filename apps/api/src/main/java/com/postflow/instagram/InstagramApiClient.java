package com.postflow.instagram;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.postflow.facebook.FacebookProperties;
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
 * Thin client over the Instagram Graph API (hosted on the Facebook Graph host). IG content
 * publishing is a two-step flow: create a media container from an image URL + caption, then
 * publish the container. IG feed posts REQUIRE an image — text-only is not supported. The IG
 * Business account is discovered from a linked Facebook Page and shares the Page access token.
 */
@Component
public class InstagramApiClient {

    private final FacebookProperties fb;
    private final RestClient graph;

    public InstagramApiClient(FacebookProperties fb) {
        this.fb = fb;
        this.graph = RestClient.builder().baseUrl(fb.graphBaseUrlOrDefault()).build();
    }

    private String ver() {
        return "/" + fb.apiVersionOrDefault();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /**
     * 이 계정을 호출할 Graph 호스트(버전 포함). {@code override} 는 계정의 instanceUrl —
     * IG 직접 로그인 계정은 graph.instagram.com 이 들어있고, FB 페이지 연결 IG 계정은 null
     * 이라 페북 Graph 호스트로 떨어진다. 발행·댓글·인사이트가 계정 종류에 맞는 호스트로 나간다.
     */
    private String graphBase(String override) {
        String host = (override != null && !override.isBlank()) ? override : fb.graphBaseUrlOrDefault();
        return host + ver();
    }

    /** The IG Business account linked to a Page (null if none / no instagram scope). */
    public IgAccount discoverIgAccount(String pageId, String pageToken) {
        // Graph 중첩 필드 '{}'는 UriComponentsBuilder가 URI 템플릿으로 오인 → 값을 직접 인코딩해 절대 URI로.
        URI uri = URI.create(fb.graphBaseUrlOrDefault() + ver() + "/" + pageId
                + "?fields=" + URLEncoder.encode("instagram_business_account{id,username,profile_picture_url}", StandardCharsets.UTF_8)
                + "&access_token=" + URLEncoder.encode(pageToken, StandardCharsets.UTF_8));
        try {
            PageIg res = graph.get().uri(uri).retrieve().body(PageIg.class);
            return res != null ? res.instagramBusinessAccount() : null;
        } catch (RestClientException e) {
            return null; // best-effort — missing scope / no IG account
        }
    }

    /**
     * Publish an image post: create a container from {@code imageUrl} (+ caption), then publish.
     * Instagram requires an image — a null/blank {@code imageUrl} throws.
     * Returns the published media id.
     */
    public String publishImage(String graphBaseOverride, String igUserId, String token,
                               String caption, String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new InstagramApiException("인스타그램은 이미지가 있어야 발행할 수 있어요. 이미지를 첨부해 주세요.");
        }
        String base = graphBase(graphBaseOverride);
        // 1) create media container
        MultiValueMap<String, String> create = new LinkedMultiValueMap<>();
        create.add("image_url", imageUrl);
        if (caption != null && !caption.isBlank()) {
            create.add("caption", caption);
        }
        create.add("access_token", token);
        String creationId;
        try {
            IgId res = graph.post().uri(URI.create(base + "/" + igUserId + "/media"))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(create)
                    .retrieve().body(IgId.class);
            creationId = res != null ? res.id() : null;
        } catch (RestClientResponseException e) {
            throw new InstagramApiException("인스타그램 미디어 생성에 실패했어요. (" + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new InstagramApiException("인스타그램 미디어 생성에 실패했어요.", e);
        }
        if (creationId == null) {
            throw new InstagramApiException("인스타그램 미디어 컨테이너 id를 받지 못했어요.");
        }
        // 2) publish the container
        MultiValueMap<String, String> publish = new LinkedMultiValueMap<>();
        publish.add("creation_id", creationId);
        publish.add("access_token", token);
        try {
            IgId res = graph.post().uri(URI.create(base + "/" + igUserId + "/media_publish"))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(publish)
                    .retrieve().body(IgId.class);
            return res != null ? res.id() : null;
        } catch (RestClientResponseException e) {
            throw new InstagramApiException("인스타그램 발행에 실패했어요. (" + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new InstagramApiException("인스타그램 발행에 실패했어요.", e);
        }
    }

    /**
     * Comments on an IG media object. Requires {@code instagram_manage_comments}.
     * A deleted media (404 / code 100) signals {@link com.postflow.social.PostDeletedException}
     * so comment automation can self-heal the target.
     */
    public java.util.List<IgComment> getComments(String graphBaseOverride, String mediaId, String token) {
        URI uri = URI.create(graphBase(graphBaseOverride) + "/" + mediaId + "/comments"
                + "?fields=" + enc("id,text,username")
                + "&limit=50"
                + "&access_token=" + enc(token));
        try {
            IgComments res = graph.get().uri(uri).retrieve().body(IgComments.class);
            return res == null || res.data() == null ? java.util.List.of() : res.data();
        } catch (RestClientResponseException e) {
            if (isDeleted(e)) {
                throw new com.postflow.social.PostDeletedException("인스타그램 게시물이 삭제됐어요.");
            }
            throw new InstagramApiException(
                    "인스타그램 댓글을 불러오지 못했어요. (" + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new InstagramApiException("인스타그램 댓글을 불러오지 못했어요.", e);
        }
    }

    /** Reply to a comment (nested under it). Requires {@code instagram_manage_comments}. */
    public String replyToComment(String graphBaseOverride, String commentId, String token, String text) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("message", text == null ? "" : text);
        form.add("access_token", token);
        try {
            IgId res = graph.post().uri(URI.create(graphBase(graphBaseOverride) + "/" + commentId + "/replies"))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve().body(IgId.class);
            return res == null ? null : res.id();
        } catch (RestClientResponseException e) {
            throw new InstagramApiException(
                    "인스타그램 답글 작성에 실패했어요. (" + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new InstagramApiException("인스타그램 답글 작성에 실패했어요.", e);
        }
    }

    /** Account profile + counts for insights. Requires {@code instagram_manage_insights}. */
    public IgProfile getProfile(String graphBaseOverride, String igUserId, String token) {
        URI uri = URI.create(graphBase(graphBaseOverride) + "/" + igUserId
                + "?fields=" + enc("username,profile_picture_url,followers_count,follows_count,media_count")
                + "&access_token=" + enc(token));
        try {
            return graph.get().uri(uri).retrieve().body(IgProfile.class);
        } catch (RestClientResponseException e) {
            throw new InstagramApiException(
                    "인스타그램 프로필을 불러오지 못했어요. (" + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new InstagramApiException("인스타그램 프로필을 불러오지 못했어요.", e);
        }
    }

    /** Recent media with engagement counts (for insights aggregation). */
    public java.util.List<IgMedia> getRecentMedia(String graphBaseOverride, String igUserId,
                                                  String token, int limit) {
        URI uri = URI.create(graphBase(graphBaseOverride) + "/" + igUserId + "/media"
                + "?fields=" + enc("id,caption,media_url,permalink,timestamp,like_count,comments_count")
                + "&limit=" + limit
                + "&access_token=" + enc(token));
        try {
            IgMediaList res = graph.get().uri(uri).retrieve().body(IgMediaList.class);
            return res == null || res.data() == null ? java.util.List.of() : res.data();
        } catch (RestClientResponseException e) {
            throw new InstagramApiException(
                    "인스타그램 게시물을 불러오지 못했어요. (" + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new InstagramApiException("인스타그램 게시물을 불러오지 못했어요.", e);
        }
    }

    /** IG "media not found" — deleted post. Graph returns 404, or 400 with error code 100. */
    private static boolean isDeleted(RestClientResponseException e) {
        int code = e.getStatusCode().value();
        String body = e.getResponseBodyAsString();
        return code == 404 || (code == 400 && (body.contains("\"code\":100") || body.contains("does not exist")));
    }

    public record IgComments(java.util.List<IgComment> data) {
    }

    public record IgComment(String id, String text, String username) {
    }

    public record IgMediaList(java.util.List<IgMedia> data) {
    }

    public record IgMedia(
            String id,
            String caption,
            @JsonProperty("media_url") String mediaUrl,
            String permalink,
            String timestamp,
            @JsonProperty("like_count") Integer likeCount,
            @JsonProperty("comments_count") Integer commentsCount) {
    }

    public record IgProfile(
            String username,
            @JsonProperty("profile_picture_url") String profilePictureUrl,
            @JsonProperty("followers_count") Integer followersCount,
            @JsonProperty("follows_count") Integer followsCount,
            @JsonProperty("media_count") Integer mediaCount) {
    }

    public record PageIg(@JsonProperty("instagram_business_account") IgAccount instagramBusinessAccount) {
    }

    public record IgAccount(
            String id,
            String username,
            @JsonProperty("profile_picture_url") String profilePictureUrl) {
    }

    public record IgId(String id) {
    }
}
