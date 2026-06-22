package com.postflow.threads.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Container creation / publish responses both return {@code {"id": ...}}. */
public record ThreadsIdResponse(
        @JsonProperty("id") String id
) {
}
