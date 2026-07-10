package com.postflow.mastodon;

import com.postflow.social.PublishException;

/** A Mastodon API call failed. Extends the shared publish exception. */
public class MastodonApiException extends PublishException {
    public MastodonApiException(String message) {
        super(message);
    }

    public MastodonApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
