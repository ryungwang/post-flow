package com.postflow.naver;

/** 네이버 검색 API 호출 실패(키 미설정·오류 등). 컨트롤러가 502로 메시지를 내려준다. */
public class NaverException extends RuntimeException {
    public NaverException(String message) {
        super(message);
    }

    public NaverException(String message, Throwable cause) {
        super(message, cause);
    }
}
