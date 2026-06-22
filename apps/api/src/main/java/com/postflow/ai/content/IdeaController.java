package com.postflow.ai.content;

import com.postflow.ai.content.HookGenerator.HookVariant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * "Today's posts" idea board — a rotating set of topic ideas, each with its top-scoring hook.
 * Deterministic (date-seeded rotation + formula hooks), so it works without API keys.
 */
@RestController
@RequestMapping("/api/ai/ideas")
public class IdeaController {

    private static final List<String> TOPIC_POOL = List.of(
            "AI 활용법", "주말 생산성", "사이드 프로젝트", "글쓰기 루틴", "독서 습관",
            "재택근무 팁", "시간 관리", "돈 관리", "운동 루틴", "마케팅 인사이트",
            "커리어 성장", "번아웃 극복", "아침 루틴", "협업 노하우", "학습법",
            "스타트업 교훈", "콘텐츠 제작", "퍼스널 브랜딩", "네트워킹", "의사결정");

    private final HookGenerator hookGenerator;

    public IdeaController(HookGenerator hookGenerator) {
        this.hookGenerator = hookGenerator;
    }

    public record Idea(String topic, HookVariant topHook) {
    }

    @GetMapping
    public List<Idea> ideas(@RequestParam(defaultValue = "5") int count,
                            @RequestParam(defaultValue = "0") int page) {
        int n = Math.max(1, Math.min(count, TOPIC_POOL.size()));
        // rotate by day-of-year (fresh daily) + page (새로고침/더보기), deterministic
        int day = LocalDate.now(ZoneId.of("Asia/Seoul")).getDayOfYear();
        int offset = Math.floorMod(day + page * n, TOPIC_POOL.size());
        List<Idea> ideas = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String topic = TOPIC_POOL.get((offset + i) % TOPIC_POOL.size());
            HookVariant top = hookGenerator.generate(topic, 1).get(0);
            ideas.add(new Idea(topic, top));
        }
        return ideas;
    }
}
