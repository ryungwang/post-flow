package com.postflow.ai.content;

import java.util.List;

/**
 * Deterministic "attention score" (0-100) for a post — a fast heuristic for how likely the
 * copy is to stop the scroll and drive engagement. No LLM call, so it works without API keys
 * and is stable for ranking generated variations and badging saved posts.
 *
 * Weights: hook 35 · length 20 · question 12 · CTA 13 · hashtags 10 · structure 10.
 */
public final class ContentScorer {

    private ContentScorer() {
    }

    private static final java.util.regex.Pattern POWER = java.util.regex.Pattern.compile(
            "비결|이유|실수|충격|무료|방법|가지|왜|사실|비밀|놓치|꿀팁|핵심|변화|결국|진짜|당신|지금|"
                    + "secret|why|how|mistake|free|stop|never|truth|proven",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    public static int score(String content, List<String> hashtags, String cta) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        int total = hook(firstLine(content)) + length(content) + question(content)
                + cta(cta) + hashtags(hashtags) + structure(content);
        return Math.max(0, Math.min(100, total));
    }

    public record Component(String label, int score, int max) {
    }

    public record ScoreAnalysis(int total, List<Component> components, List<String> tips) {
    }

    /** Full breakdown + actionable tips for raising the attention score. */
    public static ScoreAnalysis analyze(String content, List<String> hashtags, String cta) {
        if (content == null || content.isBlank()) {
            return new ScoreAnalysis(0, List.of(), List.of("본문을 입력하세요."));
        }
        String first = firstLine(content);
        int h = hook(first), len = length(content), q = question(content),
                c = cta(cta), tags = hashtags(hashtags), st = structure(content);

        List<Component> components = List.of(
                new Component("훅", h, 35),
                new Component("길이", len, 20),
                new Component("질문", q, 12),
                new Component("CTA", c, 13),
                new Component("해시태그", tags, 10),
                new Component("구조", st, 10));

        List<String> tips = new java.util.ArrayList<>();
        if (h < 25) tips.add("첫 문장에 숫자·질문·호기심 단어(비결/실수/이유)를 넣어 시선을 잡으세요.");
        if (q == 0) tips.add("본문 끝에 질문을 넣어 댓글을 유도하세요.");
        if (c == 0) tips.add("행동 유도(CTA) 한 줄을 추가하세요. (선택)");
        int n = content.length();
        if (n < 150) tips.add("본문을 조금 더 구체적으로 채우세요 (150자 이상 권장).");
        else if (n > 480) tips.add("500자에 근접했어요 — 핵심만 남겨 간결하게.");
        int tagCount = hashtags == null ? 0 : hashtags.size();
        if (tagCount < 3 || tagCount > 5) tips.add("해시태그를 3~5개로 맞추세요.");
        if (st < 6) tips.add("줄바꿈·목록으로 가독성을 높이세요.");
        if (tips.isEmpty()) tips.add("훌륭해요! 관심을 끌 요소를 골고루 갖췄습니다.");

        return new ScoreAnalysis(Math.min(100, h + len + q + c + tags + st), components, tips);
    }

    /** Hook strength of a single line, normalized to 0-100 (for ranking hook variants). */
    public static int hookScore(String line) {
        if (line == null || line.isBlank()) {
            return 0;
        }
        return Math.min(100, Math.round(hook(line.trim()) * 100f / 35f));
    }

    private static String firstLine(String content) {
        for (String line : content.split("\n")) {
            if (!line.isBlank()) {
                return line.trim();
            }
        }
        return content.trim();
    }

    /** 0-35: the first line is what stops the scroll. */
    private static int hook(String line) {
        int s = 5;
        if (line.matches(".*\\d.*")) s += 10;          // a number/stat
        if (line.contains("?")) s += 6;                 // open loop
        if (POWER.matcher(line).find()) s += 10;        // power/curiosity word
        if (hasEmoji(line)) s += 4;                      // visual stop
        return Math.min(35, s);
    }

    /** 0-20: Threads sweet spot ~150-480 chars. */
    private static int length(String content) {
        int n = content.length();
        if (n >= 150 && n <= 480) return 20;
        if (n > 480 && n <= 500) return 16;
        if (n >= 80) return 12;
        return Math.max(0, n * 8 / 80);
    }

    private static int question(String content) {
        return content.contains("?") ? 12 : 0;
    }

    private static int cta(String cta) {
        return cta != null && !cta.isBlank() ? 13 : 0;
    }

    /** 0-10: 3-5 hashtags is ideal. */
    private static int hashtags(List<String> hashtags) {
        int n = hashtags == null ? 0 : hashtags.size();
        if (n >= 3 && n <= 5) return 10;
        if (n >= 6) return 6;
        if (n >= 1) return 5;
        return 0;
    }

    /** 0-10: line breaks + list markers aid scannability. */
    private static int structure(String content) {
        int s = 0;
        if (content.contains("\n")) s += 6;
        for (String line : content.split("\n")) {
            String t = line.trim();
            if (t.matches("^([0-9]+[.)]|[-·•*]).*")) {
                s += 4;
                break;
            }
        }
        return Math.min(10, s);
    }

    private static boolean hasEmoji(String s) {
        return s.codePoints().anyMatch(cp ->
                (cp >= 0x1F300 && cp <= 0x1FAFF) || (cp >= 0x2600 && cp <= 0x27BF) || cp == 0x2728);
    }
}
