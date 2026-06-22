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
    private static final long POLL_INTERVAL_MS = 2000L;

    private final ThreadsApiClient apiClient;

    public ThreadsPublishService(ThreadsApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /** Publish text to Threads; returns the published media id. Throws on failure/timeout. */
    public String publishText(String threadsUserId, String accessToken, String text) {
        String creationId = apiClient.createTextContainer(threadsUserId, accessToken, text);
        awaitFinished(creationId, accessToken);
        return apiClient.publish(threadsUserId, accessToken, creationId);
    }

    private void awaitFinished(String containerId, String accessToken) {
        for (int attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt++) {
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
