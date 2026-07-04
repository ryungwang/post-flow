package com.postflow.threads.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 연결된 Threads 계정에 실제로 올라간 게시물 한 건(Threads Graph {@code GET /{user}/threads}).
 * PostFlow에서 만든 게 아닌, 계정의 진짜 게시물(외부 작성 포함)을 조회할 때 쓴다.
 */
public record ThreadsUserPost(
        String id,
        String text,
        String timestamp,
        String permalink,
        @JsonProperty("media_type") String mediaType,
        @JsonProperty("media_url") String mediaUrl
) {
}
