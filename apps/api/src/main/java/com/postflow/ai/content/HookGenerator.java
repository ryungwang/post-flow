package com.postflow.ai.content;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates scroll-stopping hook variants for a topic from proven copywriting formulas,
 * ranked by {@link ContentScorer#hookScore}. Deterministic (no LLM) so it works without
 * API keys; with a key the chosen hook can seed full-post generation.
 */
@Service
public class HookGenerator {

    /** Hook formulas — {@code %s} is the topic. Mix of number/contrarian/question/curiosity angles. */
    private static final List<String> FORMULAS = List.of(
            "%s, 아무도 안 알려주는 3가지",
            "%s 하지 마세요. 대신 이렇게 했더니",
            "내가 %s로 결과를 만든 방법",
            "%s에 대한 의외의 사실 하나",
            "솔직히 %s, 이게 핵심입니다",
            "%s 시작하기 전에 꼭 알아야 할 것",
            "왜 대부분 %s에서 실패할까?",
            "%s, 90%%가 놓치는 포인트",
            "%s 1년 해보고 깨달은 5가지",
            "%s의 진짜 문제는 따로 있습니다",
            "%s 초보가 가장 많이 하는 실수 3가지",
            "%s, 이 순서대로만 하세요"
    );

    public List<HookVariant> generate(String topic, int count) {
        String t = topic == null ? "" : topic.trim();
        Set<String> seen = new LinkedHashSet<>();
        List<HookVariant> variants = new ArrayList<>();
        for (String formula : FORMULAS) {
            String hook = String.format(formula, t);
            if (seen.add(hook)) {
                variants.add(new HookVariant(hook, ContentScorer.hookScore(hook)));
            }
        }
        variants.sort(Comparator.comparingInt(HookVariant::score).reversed());
        int n = Math.max(1, Math.min(count <= 0 ? 6 : count, variants.size()));
        return variants.subList(0, n);
    }

    public record HookVariant(String hook, int score) {
    }
}
