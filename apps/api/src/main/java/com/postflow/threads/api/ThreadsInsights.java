package com.postflow.threads.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Response of /{threads-user-id}/threads_insights?metric=followers_count. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ThreadsInsights(List<Metric> data) {

    // 계정 insight(followers_count 등)는 total_value, 미디어 insight(likes/views 등)는 values 배열 — 둘 다 지원.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metric(String name, TotalValue total_value, List<ValueEntry> values) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TotalValue(Long value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ValueEntry(Long value) {
    }

    /** Extract a metric value — total_value(계정) 또는 values[0](미디어). 없으면 null. */
    public Long value(String metric) {
        if (data == null) {
            return null;
        }
        return data.stream()
                .filter(m -> metric.equals(m.name()))
                .map(m -> {
                    if (m.total_value() != null && m.total_value().value() != null) {
                        return m.total_value().value();
                    }
                    if (m.values() != null && !m.values().isEmpty() && m.values().get(0) != null) {
                        return m.values().get(0).value();
                    }
                    return (Long) null;
                })
                .filter(v -> v != null)
                .findFirst().orElse(null);
    }

    public Long followersCount() {
        return value("followers_count");
    }
}
