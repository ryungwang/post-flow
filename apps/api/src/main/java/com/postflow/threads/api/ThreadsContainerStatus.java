package com.postflow.threads.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Container processing status: IN_PROGRESS / FINISHED / ERROR / EXPIRED / PUBLISHED. */
public record ThreadsContainerStatus(
        @JsonProperty("status") String status,
        @JsonProperty("error_message") String errorMessage
) {
    public boolean isFinished() {
        return "FINISHED".equals(status);
    }

    public boolean isError() {
        return "ERROR".equals(status) || "EXPIRED".equals(status);
    }
}
