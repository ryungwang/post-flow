package com.postflow.threads.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ThreadsUsername(String id, String username) {
}
