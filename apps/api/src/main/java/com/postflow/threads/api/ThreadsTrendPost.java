package com.postflow.threads.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 키워드 검색 결과 한 건(공개 게시물) — 트렌드 반영 생성·둘러보기에 사용. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ThreadsTrendPost(
        String id,
        String text,
        String username,
        String permalink,
        String timestamp,
        @JsonProperty("media_type") String mediaType
) {
}
