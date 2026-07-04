package com.postflow.threads.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** {@code GET /keyword_search} 응답 래퍼. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ThreadsTrendResponse(List<ThreadsTrendPost> data) {
}
