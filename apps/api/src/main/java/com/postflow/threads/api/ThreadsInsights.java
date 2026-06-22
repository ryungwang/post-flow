package com.postflow.threads.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Response of /{threads-user-id}/threads_insights?metric=followers_count. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ThreadsInsights(List<Metric> data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metric(String name, TotalValue total_value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TotalValue(Long value) {
    }

    /** Extract the followers_count value, or null if absent. */
    public Long followersCount() {
        if (data == null) {
            return null;
        }
        return data.stream()
                .filter(m -> "followers_count".equals(m.name()) && m.total_value() != null)
                .map(m -> m.total_value().value())
                .findFirst().orElse(null);
    }
}
