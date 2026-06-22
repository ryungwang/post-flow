package com.postflow.threads;

public class ThreadsApiException extends RuntimeException {
    public ThreadsApiException(String message) {
        super(message);
    }

    public ThreadsApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
