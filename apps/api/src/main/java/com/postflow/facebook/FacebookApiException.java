package com.postflow.facebook;

import com.postflow.social.PublishException;

/** A Facebook Graph API call failed. Extends the shared publish exception. */
public class FacebookApiException extends PublishException {
    public FacebookApiException(String message) {
        super(message);
    }

    public FacebookApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
