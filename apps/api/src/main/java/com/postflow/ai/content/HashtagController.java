package com.postflow.ai.content;

import com.postflow.post.PostRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Suggests relevant hashtags for a topic/body. Curated keyword→tags map + the user's own
 * frequently-used tags. Deterministic (no LLM) — works without API keys.
 */
@RestController
@RequestMapping("/api/ai/hashtags")
public class HashtagController {

    private static final Map<String, List<String>> CURATED = new LinkedHashMap<>();
    static {
        CURATED.put("ai", List.of("AI", "인공지능", "자동화", "생산성"));
        CURATED.put("스타트업", List.of("스타트업", "창업", "사이드프로젝트", "린스타트업"));
        CURATED.put("마케팅", List.of("마케팅", "콘텐츠마케팅", "브랜딩", "그로스"));
        CURATED.put("생산성", List.of("생산성", "루틴", "자기계발", "시간관리"));
        CURATED.put("개발", List.of("개발", "프로그래밍", "코딩", "개발자"));
        CURATED.put("글쓰기", List.of("글쓰기", "콘텐츠", "카피라이팅", "스레드"));
        CURATED.put("운동", List.of("운동", "헬스", "건강", "루틴"));
        CURATED.put("여행", List.of("여행", "여행스타그램", "국내여행", "여행팁"));
        CURATED.put("돈", List.of("재테크", "투자", "돈관리", "경제적자유"));
        CURATED.put("커리어", List.of("커리어", "이직", "성장", "직장인"));
    }
    private static final List<String> DEFAULTS = List.of("스레드", "Threads", "성장", "인사이트", "팁");

    private final PostRepository postRepository;

    public HashtagController(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @PostMapping
    public List<String> suggest(@AuthenticationPrincipal Long userId, @RequestBody Map<String, Object> body) {
        String text = (String.valueOf(body.getOrDefault("topic", "")) + " "
                + String.valueOf(body.getOrDefault("content", ""))).toLowerCase();

        Set<String> tags = new LinkedHashSet<>();
        CURATED.forEach((key, values) -> {
            if (text.contains(key)) {
                tags.addAll(values);
            }
        });

        // user's own frequently-used hashtags
        Map<String, Integer> freq = new LinkedHashMap<>();
        postRepository.findByUserIdOrderByCreatedAtDesc(userId).forEach(p ->
                p.getHashtags().forEach(h -> freq.merge(h, 1, Integer::sum)));
        freq.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(5)
                .forEach(e -> tags.add(e.getKey()));

        if (tags.size() < 5) {
            tags.addAll(DEFAULTS);
        }
        return new ArrayList<>(tags).stream().limit(10).toList();
    }
}
