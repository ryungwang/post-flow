package com.postflow.threads;

import com.postflow.threads.api.ThreadsContainerStatus;
import org.springframework.stereotype.Service;

/**
 * Two-step Threads publish: create container → poll until FINISHED → publish.
 * The container is created at publish time (containers expire after 24h, so they can't
 * be pre-created for far-future scheduled posts). Text posts usually process instantly.
 */
@Service
public class ThreadsPublishService {

    private static final int MAX_POLL_ATTEMPTS = 10;
    private static final int MAX_POLL_ATTEMPTS_VIDEO = 60; // 영상 인코딩은 더 오래 걸림(최대 ~2분)
    private static final long POLL_INTERVAL_MS = 2000L;

    private final ThreadsApiClient apiClient;

    public ThreadsPublishService(ThreadsApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /** Publish text to Threads; returns the published media id. Throws on failure/timeout. */
    public String publishText(String threadsUserId, String accessToken, String text) {
        return publish(threadsUserId, accessToken, text, null);
    }

    /**
     * Publish to Threads; creates an IMAGE container when {@code mediaUrl} is present
     * (must be a publicly reachable URL), otherwise a TEXT container. Returns the media id.
     */
    public String publish(String threadsUserId, String accessToken, String text, String mediaUrl) {
        boolean hasMedia = mediaUrl != null && !mediaUrl.isBlank();
        boolean isVideo = hasMedia && isVideoUrl(mediaUrl);
        String creationId;
        if (!hasMedia) {
            creationId = apiClient.createTextContainer(threadsUserId, accessToken, text);
        } else if (isVideo) {
            creationId = apiClient.createVideoContainer(threadsUserId, accessToken, text, mediaUrl);
        } else {
            creationId = apiClient.createImageContainer(threadsUserId, accessToken, text, mediaUrl);
        }
        awaitFinished(creationId, accessToken, isVideo ? MAX_POLL_ATTEMPTS_VIDEO : MAX_POLL_ATTEMPTS);
        return apiClient.publish(threadsUserId, accessToken, creationId);
    }

    private boolean isVideoUrl(String url) {
        String u = url.toLowerCase();
        int q = u.indexOf('?');
        if (q >= 0) {
            u = u.substring(0, q);
        }
        return u.endsWith(".mp4") || u.endsWith(".mov");
    }

    /** Publish a reply to a comment/media; returns the published reply id. */
    public String publishReply(String threadsUserId, String accessToken, String text, String replyToId) {
        String creationId = apiClient.createReplyContainer(threadsUserId, accessToken, text, replyToId);
        awaitFinished(creationId, accessToken, MAX_POLL_ATTEMPTS);
        return apiClient.publish(threadsUserId, accessToken, creationId);
    }

    private void awaitFinished(String containerId, String accessToken, int maxAttempts) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            ThreadsContainerStatus status = apiClient.getContainerStatus(containerId, accessToken);
            if (status != null && status.isFinished()) {
                return;
            }
            if (status != null && status.isError()) {
                throw new ThreadsApiException("Container processing failed: " + status.status()
                        + (status.errorMessage() != null ? " (" + status.errorMessage() + ")" : ""));
            }
            sleep();
        }
        throw new ThreadsApiException("Container did not reach FINISHED within timeout");
    }

    private void sleep() {
        try {
            Thread.sleep(POLL_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ThreadsApiException("Interrupted while polling container status", e);
        }
    }
}
