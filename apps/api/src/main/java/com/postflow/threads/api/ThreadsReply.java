package com.postflow.threads.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A reply (comment) on a Threads post. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ThreadsReply(String id, String text, String username) {
}
