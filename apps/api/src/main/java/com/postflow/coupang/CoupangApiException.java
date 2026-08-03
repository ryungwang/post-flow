package com.postflow.coupang;

/** 쿠팡 Open API 호출 실패. IllegalStateException을 상속해 GlobalExceptionHandler가 메시지를 그대로 전달한다. */
public class CoupangApiException extends IllegalStateException {
    public CoupangApiException(String message) {
        super(message);
    }

    public CoupangApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
