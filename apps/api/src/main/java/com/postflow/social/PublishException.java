package com.postflow.social;

/**
 * Generic publish failure across any provider. Provider-specific exceptions
 * (e.g. ThreadsApiException, BlueskyApiException) extend this so the shared
 * publish pipeline can catch one type regardless of platform.
 */
public class PublishException extends RuntimeException {
    public PublishException(String message) {
        super(message);
    }

    public PublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
