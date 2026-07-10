package com.postflow.mastodon;

import com.fasterxml.jackson.annotation.JsonProperty;
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
 * personal access token. Surface: verify credentials (connect), status create/delete, and
 * media upload (image). A 401 surfaces as {@link MastodonAuthException}.
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
            String url) {
    }

    public record MastodonStatus(String id, String url) {
    }

    public record MastodonMedia(String id, String type, List<Object> ignored) {
    }
}
