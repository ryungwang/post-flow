package com.postflow.instagram;

import com.postflow.social.PublishException;

/** An Instagram Graph API call failed. Extends the shared publish exception. */
public class InstagramApiException extends PublishException {
    public InstagramApiException(String message) {
        super(message);
    }

    public InstagramApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
