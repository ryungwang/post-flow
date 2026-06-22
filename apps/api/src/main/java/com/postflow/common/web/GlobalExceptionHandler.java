package com.postflow.common.web;

import com.anthropic.errors.AnthropicException;
import com.postflow.ai.content.ContentGenerationException;
import com.postflow.user.PlanLimitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Translates provider/domain errors into clean JSON {@code {error, message}} for the UI. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AnthropicException.class)
    public ResponseEntity<Map<String, String>> anthropic(AnthropicException e) {
        String raw = e.getMessage() == null ? "" : e.getMessage();
        String message;
        if (raw.contains("credit balance")) {
            message = "AI 제공자(Anthropic) 크레딧이 부족합니다. 결제·크레딧 충전 후 다시 시도하세요.";
        } else if (raw.contains("authentication") || raw.contains("401")) {
            message = "AI 제공자 인증에 실패했어요. ANTHROPIC_API_KEY를 확인하세요.";
        } else if (raw.contains("rate") || raw.contains("overloaded") || raw.contains("529")) {
            message = "AI 제공자가 혼잡합니다. 잠시 후 다시 시도하세요.";
        } else {
            message = "AI 생성 중 제공자 오류가 발생했어요. 잠시 후 다시 시도하세요.";
        }
        log.warn("Anthropic error: {}", raw.split("\n")[0]);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "llm_error", "message", message));
    }

    @ExceptionHandler(PlanLimitException.class)
    public ResponseEntity<Map<String, String>> planLimit(PlanLimitException e) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(Map.of("error", "plan_limit", "message", e.getMessage()));
    }

    @ExceptionHandler(ContentGenerationException.class)
    public ResponseEntity<Map<String, String>> generation(ContentGenerationException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "generation_failed", "message", "AI 응답 처리에 실패했어요. 다시 시도해 주세요."));
    }

    /** Resource lookups / ownership checks throw IllegalArgumentException("... not found") → 404. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> notFound(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "not_found", "message", e.getMessage() != null ? e.getMessage() : "찾을 수 없어요."));
    }

    /** Other illegal states (e.g. payment not configured) → 400. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> badState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "bad_request", "message", e.getMessage() != null ? e.getMessage() : "요청을 처리할 수 없어요."));
    }
}
