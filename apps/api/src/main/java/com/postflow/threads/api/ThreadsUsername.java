package com.postflow.threads.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Threads /me profile fields. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ThreadsUsername(
        String id,
        String username,
        String name,
        @JsonProperty("threads_profile_picture_url") String profilePictureUrl,
        @JsonProperty("threads_biography") String biography) {
}
