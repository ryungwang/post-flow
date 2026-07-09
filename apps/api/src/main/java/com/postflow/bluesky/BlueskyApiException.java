package com.postflow.bluesky;

import com.postflow.social.PublishException;

/** A Bluesky (AT Protocol) call failed. Extends the shared publish exception. */
public class BlueskyApiException extends PublishException {
    public BlueskyApiException(String message) {
        super(message);
    }

    public BlueskyApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
