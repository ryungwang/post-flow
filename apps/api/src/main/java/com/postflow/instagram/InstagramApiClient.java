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
