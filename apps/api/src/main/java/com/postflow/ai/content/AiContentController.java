package com.postflow.ai.content;

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

@RestController
@RequestMapping("/api/ai")
public class AiContentController {

    private final ContentGenerationService contentGenerationService;

    public AiContentController(ContentGenerationService contentGenerationService) {
        this.contentGenerationService = contentGenerationService;
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
