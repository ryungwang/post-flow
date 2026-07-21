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
import org.springframework.web.util.UriComponentsBuilder;

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

    /** The IG Business account linked to a Page (null if none / no instagram scope). */
    public IgAccount discoverIgAccount(String pageId, String pageToken) {
        String uri = UriComponentsBuilder.fromPath(ver() + "/" + pageId)
                .queryParam("fields", "instagram_business_account{id,username,profile_picture_url}")
                .queryParam("access_token", pageToken)
                .build().toUriString();
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
    public String publishImage(String igUserId, String pageToken, String caption, String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new InstagramApiException("인스타그램은 이미지가 있어야 발행할 수 있어요. 이미지를 첨부해 주세요.");
        }
        // 1) create media container
        MultiValueMap<String, String> create = new LinkedMultiValueMap<>();
        create.add("image_url", imageUrl);
        if (caption != null && !caption.isBlank()) {
            create.add("caption", caption);
        }
        create.add("access_token", pageToken);
        String creationId;
        try {
            IgId res = graph.post().uri(ver() + "/" + igUserId + "/media")
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
        publish.add("access_token", pageToken);
        try {
            IgId res = graph.post().uri(ver() + "/" + igUserId + "/media_publish")
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
    public java.util.List<IgComment> getComments(String mediaId, String pageToken) {
        String uri = UriComponentsBuilder.fromPath(ver() + "/" + mediaId + "/comments")
                .queryParam("fields", "id,text,username")
                .queryParam("limit", 50)
                .queryParam("access_token", pageToken)
                .build().toUriString();
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
    public String replyToComment(String commentId, String pageToken, String text) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("message", text == null ? "" : text);
        form.add("access_token", pageToken);
        try {
            IgId res = graph.post().uri(ver() + "/" + commentId + "/replies")
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
    public IgProfile getProfile(String igUserId, String pageToken) {
        String uri = UriComponentsBuilder.fromPath(ver() + "/" + igUserId)
                .queryParam("fields", "username,profile_picture_url,followers_count,follows_count,media_count")
                .queryParam("access_token", pageToken)
                .build().toUriString();
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
    public java.util.List<IgMedia> getRecentMedia(String igUserId, String pageToken, int limit) {
        String uri = UriComponentsBuilder.fromPath(ver() + "/" + igUserId + "/media")
                .queryParam("fields", "id,caption,media_url,permalink,timestamp,like_count,comments_count")
                .queryParam("limit", limit)
                .queryParam("access_token", pageToken)
                .build().toUriString();
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
