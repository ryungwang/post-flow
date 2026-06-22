package com.postflow.ai.content;

import com.postflow.ai.content.HookGenerator.HookVariant;
import com.postflow.ai.content.dto.GenerateContentRequest;
import com.postflow.ai.content.dto.GenerateContentResponse;
import com.postflow.ai.content.dto.GenerateSeriesRequest;
import com.postflow.ai.content.dto.GenerateSeriesResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiContentController {

    private final ContentGenerationService contentGenerationService;
    private final HookGenerator hookGenerator;

    public AiContentController(ContentGenerationService contentGenerationService,
                              HookGenerator hookGenerator) {
        this.contentGenerationService = contentGenerationService;
        this.hookGenerator = hookGenerator;
    }

    /** Ranked hook variants for a topic (formula-based, scored — works without API keys). */
    @PostMapping("/hooks")
    public List<HookVariant> hooks(@RequestBody Map<String, Object> body) {
        String topic = String.valueOf(body.getOrDefault("topic", "")).trim();
        int count = body.get("count") instanceof Number n ? n.intValue() : 6;
        return hookGenerator.generate(topic, count);
    }

    /** Attention-score breakdown + improvement tips (heuristic — works without API keys). */
    @PostMapping("/score")
    @SuppressWarnings("unchecked")
    public ContentScorer.ScoreAnalysis score(@RequestBody Map<String, Object> body) {
        String content = String.valueOf(body.getOrDefault("content", ""));
        List<String> hashtags = body.get("hashtags") instanceof List<?> l ? (List<String>) l : List.of();
        String cta = body.get("cta") != null ? String.valueOf(body.get("cta")) : null;
        return ContentScorer.analyze(content, hashtags, cta);
    }

    @PostMapping("/generate")
    public GenerateContentResponse generate(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GenerateContentRequest request) {
        return contentGenerationService.generate(userId, request);
    }

    @PostMapping("/series")
    public GenerateSeriesResponse series(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GenerateSeriesRequest request) {
        return contentGenerationService.generateSeries(userId, request.topic(), request.days());
    }
}
