package com.postflow.threads.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** {@code GET /{user}/threads} 응답 래퍼 (data 배열 + 커서 페이징). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ThreadsUserPostsResponse(List<ThreadsUserPost> data, Paging paging) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Paging(Cursors cursors) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cursors(String before, String after) {
    }

    /** 다음 페이지 커서(없으면 null = 마지막 페이지). */
    public String afterCursor() {
        return paging != null && paging.cursors() != null ? paging.cursors().after() : null;
    }
}
