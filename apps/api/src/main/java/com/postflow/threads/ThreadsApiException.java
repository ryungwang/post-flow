package com.postflow.threads;

import com.postflow.social.PublishException;

public class ThreadsApiException extends PublishException {
    public ThreadsApiException(String message) {
        super(message);
    }

    public ThreadsApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
