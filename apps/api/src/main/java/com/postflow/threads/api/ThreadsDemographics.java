package com.postflow.threads.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /{user}/threads_insights?metric=follower_demographics&breakdown=age|gender|country|city} 응답.
 * 구조: data[].total_value.breakdowns[].results[] 에 dimension_values(라벨) + value(수).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ThreadsDemographics(List<Metric> data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metric(String name, TotalValue total_value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TotalValue(List<Breakdown> breakdowns) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Breakdown(List<String> dimension_keys, List<Result> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(List<String> dimension_values, Long value) {
    }

    /** 한 항목(라벨=연령대/성별/국가 등, 값=팔로워 수). */
    public record Entry(String label, long value) {
    }

    /** follower_demographics 결과를 값 내림차순 목록으로 평탄화. 비어있으면 빈 목록. */
    public List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        if (data == null) {
            return out;
        }
        for (Metric m : data) {
            if (m.total_value() == null || m.total_value().breakdowns() == null) {
                continue;
            }
            for (Breakdown b : m.total_value().breakdowns()) {
                if (b.results() == null) {
                    continue;
                }
                for (Result r : b.results()) {
                    String label = r.dimension_values() != null && !r.dimension_values().isEmpty()
                            ? r.dimension_values().get(0) : "기타";
                    out.add(new Entry(label, r.value() == null ? 0 : r.value()));
                }
            }
        }
        out.sort((a, c) -> Long.compare(c.value(), a.value()));
        return out;
    }
}
