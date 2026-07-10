package com.postflow.linkedin;

import com.postflow.social.PublishException;

/** A LinkedIn API call failed. Extends the shared publish exception. */
public class LinkedInApiException extends PublishException {
    public LinkedInApiException(String message) {
        super(message);
    }

    public LinkedInApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
