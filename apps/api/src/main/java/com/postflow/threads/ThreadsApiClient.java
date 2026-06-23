package com.postflow.threads;

import com.postflow.threads.api.ThreadsContainerStatus;
import com.postflow.threads.api.ThreadsIdResponse;
import com.postflow.threads.api.ThreadsReply;
import com.postflow.threads.api.ThreadsRepliesResponse;
import com.postflow.threads.api.ThreadsTokenResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Thin client over the Threads Graph API (token exchange + 2-step publishing).
 * Base host {@code graph.threads.net}; all calls authenticate with an access token.
 */
@Component
public class ThreadsApiClient {

    private final ThreadsProperties properties;
    private final RestClient graph;

    public ThreadsApiClient(ThreadsProperties properties) {
        this.properties = properties;
        this.graph = RestClient.builder()
                .baseUrl(properties.graphBaseUrlOrDefault())
                .build();
    }

    /** Step 1: authorization code → short-lived token (also returns the Threads user id). */
    public ThreadsTokenResponse exchangeCodeForShortLivedToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.appId());
        form.add("client_secret", properties.appSecret());
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", properties.redirectUri());
        form.add("code", code);
        return post("/oauth/access_token", form, ThreadsTokenResponse.class);
    }

    /** Step 2: short-lived → long-lived (60-day) token. */
    public ThreadsTokenResponse exchangeForLongLivedToken(String shortLivedToken) {
        try {
            return graph.get()
                    .uri(b -> b.path("/access_token")
                            .queryParam("grant_type", "th_exchange_token")
                            .queryParam("client_secret", properties.appSecret())
                            .queryParam("access_token", shortLivedToken)
                            .build())
                    .retrieve()
                    .body(ThreadsTokenResponse.class);
        } catch (RestClientException e) {
            throw new ThreadsApiException("Failed to exchange for long-lived token", e);
        }
    }

    /** Renew a long-lived token in place (extends another 60 days). */
    public ThreadsTokenResponse refreshLongLivedToken(String longLivedToken) {
        try {
            return graph.get()
                    .uri(b -> b.path("/refresh_access_token")
                            .queryParam("grant_type", "th_refresh_token")
                            .queryParam("access_token", longLivedToken)
                            .build())
                    .retrieve()
                    .body(ThreadsTokenResponse.class);
        } catch (RestClientException e) {
            throw new ThreadsApiException("Failed to refresh long-lived token", e);
        }
    }

    /** Create a text media container; returns its creation id. */
    public String createTextContainer(String threadsUserId, String accessToken, String text) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("media_type", "TEXT");
        form.add("text", text);
        form.add("access_token", accessToken);
        ThreadsIdResponse res = postPath("/{id}/threads", threadsUserId, form);
        return res.id();
    }

    /** Create an image media container; returns its creation id. Image URL must be public. */
    public String createImageContainer(String threadsUserId, String accessToken, String text, String imageUrl) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("media_type", "IMAGE");
        form.add("image_url", imageUrl);
        if (text != null && !text.isBlank()) {
            form.add("text", text);
        }
        form.add("access_token", accessToken);
        ThreadsIdResponse res = postPath("/{id}/threads", threadsUserId, form);
        return res.id();
    }

    /** Poll a container's processing status. */
    public ThreadsContainerStatus getContainerStatus(String containerId, String accessToken) {
        try {
            return graph.get()
                    .uri(b -> b.path("/{id}")
                            .queryParam("fields", "status,error_message")
                            .queryParam("access_token", accessToken)
                            .build(containerId))
                    .retrieve()
                    .body(ThreadsContainerStatus.class);
        } catch (RestClientException e) {
            throw new ThreadsApiException("Failed to fetch container status", e);
        }
    }

    /** Best-effort fetch of the connected account's @username. */
    public String fetchUsername(String accessToken) {
        com.postflow.threads.api.ThreadsUsername me = fetchProfile(accessToken);
        return me != null ? me.username() : null;
    }

    /** Best-effort fetch of the account profile (username, name, picture, biography). */
    public com.postflow.threads.api.ThreadsUsername fetchProfile(String accessToken) {
        try {
            return graph.get()
                    .uri(b -> b.path("/me")
                            .queryParam("fields", "id,username,name,threads_profile_picture_url,threads_biography")
                            .queryParam("access_token", accessToken).build())
                    .retrieve()
                    .body(com.postflow.threads.api.ThreadsUsername.class);
        } catch (RestClientException e) {
            return null;
        }
    }

    /** Best-effort follower count via insights (requires threads_manage_insights). Null on failure. */
    public Long fetchFollowers(String threadsUserId, String accessToken) {
        try {
            com.postflow.threads.api.ThreadsInsights insights = graph.get()
                    .uri(b -> b.path("/{id}/threads_insights")
                            .queryParam("metric", "followers_count")
                            .queryParam("access_token", accessToken)
                            .build(threadsUserId))
                    .retrieve()
                    .body(com.postflow.threads.api.ThreadsInsights.class);
            return insights != null ? insights.followersCount() : null;
        } catch (RestClientException e) {
            return null;
        }
    }

    /** Best-effort account engagement insights (views/likes/replies/reposts/quotes) over a window. */
    public com.postflow.threads.api.ThreadsInsights fetchEngagement(String threadsUserId, String accessToken,
                                                                    long sinceEpoch, long untilEpoch) {
        try {
            return graph.get()
                    .uri(b -> b.path("/{id}/threads_insights")
                            .queryParam("metric", "views,likes,replies,reposts,quotes")
                            .queryParam("since", sinceEpoch)
                            .queryParam("until", untilEpoch)
                            .queryParam("access_token", accessToken)
                            .build(threadsUserId))
                    .retrieve()
                    .body(com.postflow.threads.api.ThreadsInsights.class);
        } catch (RestClientException e) {
            return null;
        }
    }

    /** List replies (comments) on a published media. */
    public List<ThreadsReply> getReplies(String mediaId, String accessToken) {
        try {
            ThreadsRepliesResponse res = graph.get()
                    .uri(b -> b.path("/{id}/replies")
                            .queryParam("fields", "id,text,username")
                            .queryParam("access_token", accessToken)
                            .build(mediaId))
                    .retrieve()
                    .body(ThreadsRepliesResponse.class);
            return res != null && res.data() != null ? res.data() : List.of();
        } catch (RestClientException e) {
            throw new ThreadsApiException("Failed to fetch replies", e);
        }
    }

    /** Create a reply container to a given comment/media; returns its creation id. */
    public String createReplyContainer(String threadsUserId, String accessToken, String text, String replyToId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("media_type", "TEXT");
        form.add("text", text);
        form.add("reply_to_id", replyToId);
        form.add("access_token", accessToken);
        ThreadsIdResponse res = postPath("/{id}/threads", threadsUserId, form);
        return res.id();
    }

    /** Publish a finished container; returns the published media id. */
    public String publish(String threadsUserId, String accessToken, String creationId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("creation_id", creationId);
        form.add("access_token", accessToken);
        ThreadsIdResponse res = postPath("/{id}/threads_publish", threadsUserId, form);
        return res.id();
    }

    private <T> T post(String path, MultiValueMap<String, String> form, Class<T> type) {
        try {
            return graph.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(type);
        } catch (RestClientException e) {
            throw new ThreadsApiException("Threads API POST failed: " + path, e);
        }
    }

    private ThreadsIdResponse postPath(String pathTemplate, String idVar,
                                       MultiValueMap<String, String> form) {
        try {
            return graph.post()
                    .uri(pathTemplate, idVar)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(ThreadsIdResponse.class);
        } catch (RestClientException e) {
            throw new ThreadsApiException("Threads API POST failed: " + pathTemplate, e);
        }
    }
}
