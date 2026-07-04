package com.postflow.threads.dto;

import com.postflow.threads.api.ThreadsTrendPost;

import java.util.List;

/**
 * 키워드 트렌드 검색 결과. {@code available=false}면 Threads 앱이 threads_keyword_search
 * 권한 미보유(권한 추가 + 재연결 필요) — 프론트가 안내.
 */
public record TrendResult(boolean available, List<ThreadsTrendPost> posts) {
    public static TrendResult ok(List<ThreadsTrendPost> posts) {
        return new TrendResult(true, posts);
    }

    public static TrendResult unavailable() {
        return new TrendResult(false, List.of());
    }
}
