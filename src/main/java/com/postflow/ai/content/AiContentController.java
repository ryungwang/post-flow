package com.postflow.ai.content;

import com.postflow.ai.content.dto.GenerateContentRequest;
import com.postflow.ai.content.dto.GenerateContentResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
            // TODO: replace with authenticated principal once Google OAuth login lands.
            @RequestHeader(name = "X-User-Id", defaultValue = "1") Long userId,
            @Valid @RequestBody GenerateContentRequest request) {
        return contentGenerationService.generate(userId, request);
    }
}
