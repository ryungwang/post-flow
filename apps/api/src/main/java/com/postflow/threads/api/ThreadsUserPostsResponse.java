package com.postflow.threads.api;

import java.util.List;

/** {@code GET /{user}/threads} 응답 래퍼 (data 배열). */
public record ThreadsUserPostsResponse(List<ThreadsUserPost> data) {
}
