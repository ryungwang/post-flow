package com.postflow.mastodon;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.util.List;

/**
 * Thin client over the Mastodon REST API. Mastodon is federated — every account lives on a
 * different instance host — so each call takes the account's {@code instanceUrl} base and a
 * personal access token. Surface: verify credentials (connect), status create/delete,
 * media upload (image), and reads (own statuses, mention notifications).
 * A 401 surfaces as {@link MastodonAuthException}.
 */
@Component
public class MastodonApiClient {

    private final RestClient http = RestClient.create();
    private final RestClient downloader = RestClient.create(); // arbitrary media URLs

    /** Confirm the token and read the account profile. instanceUrl = https://host (no trailing slash). */
    public MastodonAccount verifyCredentials(String instanceUrl, String token) {
        try {
            return http.get().uri(URI.create(instanceUrl + "/api/v1/accounts/verify_credentials"))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve().body(MastodonAccount.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                throw new MastodonAuthException("액세스 토큰이 올바르지 않아요.");
            }
            throw new MastodonApiException("마스토돈 계정 확인에 실패했어요. (" + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new MastodonApiException("마스토돈 인스턴스에 연결하지 못했어요. 주소를 확인해 주세요.", e);
        }
    }

    /**
     * Post a status (toot) — text plus an optional image (uploaded first, then attached).
     * Returns the status id. 401 → {@link MastodonAuthException}.
     */
    public MastodonStatus createStatus(String instanceUrl, String token, String text, String mediaUrl) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("status", text == null ? "" : text);
        if (isImage(mediaUrl)) {
            String mediaId = uploadMedia(instanceUrl, token, mediaUrl);
            if (mediaId != null) {
                form.add("media_ids[]", mediaId);
            }
        }
        try {
            return http.post().uri(URI.create(instanceUrl + "/api/v1/statuses"))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve().body(MastodonStatus.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                throw new MastodonAuthException("액세스 토큰이 만료됐어요.");
            }
            throw new MastodonApiException("마스토돈 발행에 실패했어요. (" + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new MastodonApiException("마스토돈 발행에 실패했어요.", e);
        }
    }

    /** Delete a status by id. Missing (404) is treated as already gone (idempotent). */
    public void deleteStatus(String instanceUrl, String token, String statusId) {
        try {
            http.delete().uri(URI.create(instanceUrl + "/api/v1/statuses/" + statusId))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            int code = e.getStatusCode().value();
            if (code == 401) {
                throw new MastodonAuthException("액세스 토큰이 만료됐어요.");
            }
            if (code == 404) {
                return;
            }
            throw new MastodonApiException("마스토돈 게시물 삭제에 실패했어요. (" + code + ")", e);
        } catch (RestClientException e) {
            throw new MastodonApiException("마스토돈 게시물 삭제에 실패했어요.", e);
        }
    }

    /** Recent statuses authored by an account (excludes boosts/replies — we list our own posts). */
    public List<MastodonStatusItem> getAccountStatuses(String instanceUrl, String token,
                                                       String accountId, int limit) {
        String uri = instanceUrl + "/api/v1/accounts/" + accountId
                + "/statuses?limit=" + limit + "&exclude_reblogs=true&exclude_replies=true";
        return getList(uri, token, new ParameterizedTypeReference<List<MastodonStatusItem>>() {},
                "마스토돈 게시물을 불러오지 못했어요.");
    }

    /**
     * Direct replies to one of our statuses. Mastodon returns the whole thread
     * ({@code ancestors} + {@code descendants}); we keep only the statuses replying straight to
     * this one, so a nested sub-thread doesn't get auto-replied to as if it were a top comment.
     */
    public List<MastodonStatusItem> getStatusReplies(String instanceUrl, String token, String statusId) {
        String uri = instanceUrl + "/api/v1/statuses/" + statusId + "/context";
        MastodonContext context;
        try {
            context = http.get().uri(URI.create(uri))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve().body(MastodonContext.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                throw new MastodonAuthException("액세스 토큰이 만료됐어요.");
            }
            if (e.getStatusCode().value() == 404) {
                return List.of(); // 원 게시물이 지워짐 → 댓글도 없음
            }
            throw new MastodonApiException(
                    "마스토돈 댓글을 불러오지 못했어요. (" + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new MastodonApiException("마스토돈 댓글을 불러오지 못했어요.", e);
        }
        if (context == null || context.descendants() == null) {
            return List.of();
        }
        return context.descendants().stream()
                .filter(s -> statusId.equals(s.inReplyToId()))
                .toList();
    }

    /** Post a reply to an existing status. Text only — auto-replies don't attach media. */
    public MastodonStatus createReply(String instanceUrl, String token, String text, String inReplyToId) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("status", text == null ? "" : text);
        form.add("in_reply_to_id", inReplyToId);
        try {
            return http.post().uri(URI.create(instanceUrl + "/api/v1/statuses"))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve().body(MastodonStatus.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                throw new MastodonAuthException("액세스 토큰이 만료됐어요.");
            }
            throw new MastodonApiException(
                    "마스토돈 답글 작성에 실패했어요. (" + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new MastodonApiException("마스토돈 답글 작성에 실패했어요.", e);
        }
    }

    /** Recent mention notifications (someone mentioned us in a status). */
    public List<MastodonNotification> getMentions(String instanceUrl, String token, int limit) {
        String uri = instanceUrl + "/api/v1/notifications?limit=" + limit + "&types[]=mention";
        return getList(uri, token, new ParameterizedTypeReference<List<MastodonNotification>>() {},
                "마스토돈 멘션을 불러오지 못했어요.");
    }

    private <T> List<T> getList(String uri, String token, ParameterizedTypeReference<List<T>> type,
                                String failureMessage) {
        try {
            List<T> body = http.get().uri(URI.create(uri))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve().body(type);
            return body == null ? List.of() : body;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                throw new MastodonAuthException("액세스 토큰이 만료됐어요.");
            }
            if (e.getStatusCode().value() == 403) {
                throw new MastodonApiException(
                        "이 앱 토큰에 조회 권한이 없어요. 앱 범위에 read 권한을 포함해 토큰을 다시 발급해 주세요.", e);
            }
            throw new MastodonApiException(failureMessage + " (" + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new MastodonApiException(failureMessage, e);
        }
    }

    /** Download the image and upload it as media (multipart); returns the media id for attaching. */
    private String uploadMedia(String instanceUrl, String token, String mediaUrl) {
        byte[] bytes;
        try {
            bytes = downloader.get().uri(URI.create(mediaUrl))
                    .header(HttpHeaders.USER_AGENT, "PostFlow/1.0 (+https://postflow.synub.io)")
                    .retrieve().body(byte[].class);
        } catch (RestClientException e) {
            throw new MastodonApiException("이미지를 불러오지 못했어요(URL 접근 불가).", e);
        }
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        String filename = "image" + extensionFor(mediaUrl);
        ByteArrayResource file = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file);
        try {
            MastodonMedia media = http.post().uri(URI.create(instanceUrl + "/api/v2/media"))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve().body(MastodonMedia.class);
            return media != null ? media.id() : null;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                throw new MastodonAuthException("액세스 토큰이 만료됐어요.");
            }
            throw new MastodonApiException("마스토돈 이미지 업로드에 실패했어요. (" + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new MastodonApiException("마스토돈 이미지 업로드에 실패했어요.", e);
        }
    }

    private static boolean isImage(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String u = stripQuery(url).toLowerCase();
        return u.endsWith(".jpg") || u.endsWith(".jpeg") || u.endsWith(".png")
                || u.endsWith(".webp") || u.endsWith(".gif");
    }

    private static String extensionFor(String url) {
        String u = stripQuery(url).toLowerCase();
        int dot = u.lastIndexOf('.');
        return dot >= 0 ? u.substring(dot) : ".jpg";
    }

    private static String stripQuery(String url) {
        int q = url.indexOf('?');
        return q >= 0 ? url.substring(0, q) : url;
    }

    public record MastodonAccount(
            String id,
            String username,
            String acct,
            @JsonProperty("display_name") String displayName,
            String avatar,
            String url,
            @JsonProperty("followers_count") Integer followersCount,
            @JsonProperty("following_count") Integer followingCount,
            @JsonProperty("statuses_count") Integer statusesCount) {
    }

    public record MastodonStatus(String id, String url) {
    }

    /** A status as returned by the timeline/notification endpoints (richer than {@link MastodonStatus}). */
    public record MastodonStatusItem(
            String id,
            String content, // HTML
            String url,
            @JsonProperty("created_at") String createdAt,
            @JsonProperty("favourites_count") Integer favouritesCount,
            @JsonProperty("reblogs_count") Integer reblogsCount,
            @JsonProperty("replies_count") Integer repliesCount,
            @JsonProperty("in_reply_to_id") String inReplyToId,
            @JsonProperty("media_attachments") List<MastodonAttachment> mediaAttachments,
            MastodonAccount account) {
    }

    public record MastodonAttachment(
            String id,
            String type,
            String url,
            @JsonProperty("preview_url") String previewUrl) {
    }

    /** A status thread: statuses above ({@code ancestors}) and below ({@code descendants}) one status. */
    public record MastodonContext(
            List<MastodonStatusItem> ancestors,
            List<MastodonStatusItem> descendants) {
    }

    /** A notification (we only request {@code mention} types). */
    public record MastodonNotification(
            String id,
            String type,
            @JsonProperty("created_at") String createdAt,
            MastodonAccount account,
            MastodonStatusItem status) {
    }

    public record MastodonMedia(String id, String type, List<Object> ignored) {
    }
}
