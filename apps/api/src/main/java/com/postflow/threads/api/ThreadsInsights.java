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

    /** Extract a metric's total value, or null if absent. */
    public Long value(String metric) {
        if (data == null) {
            return null;
        }
        return data.stream()
                .filter(m -> metric.equals(m.name()) && m.total_value() != null)
                .map(m -> m.total_value().value())
                .findFirst().orElse(null);
    }

    public Long followersCount() {
        return value("followers_count");
    }
}
