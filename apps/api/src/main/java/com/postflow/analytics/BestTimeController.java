package com.postflow.analytics;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Recommended posting windows for Threads. Heuristic baseline (works without keys); once
 * real Insights data accumulates this can be blended with the user's own engagement.
 */
@RestController
@RequestMapping("/analytics/best-times")
public class BestTimeController {

    public record BestTime(String label, int score) {
    }

    private static final List<BestTime> SLOTS = List.of(
            new BestTime("평일 저녁 7–9시", 95),
            new BestTime("평일 점심 12–1시", 88),
            new BestTime("평일 오전 8–9시", 82),
            new BestTime("주말 오전 10–11시", 76),
            new BestTime("평일 밤 9–11시", 70),
            new BestTime("주말 저녁 8–10시", 64)
    );

    @GetMapping
    public List<BestTime> bestTimes() {
        return SLOTS;
    }
}
