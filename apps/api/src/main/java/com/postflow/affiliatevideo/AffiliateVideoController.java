package com.postflow.affiliatevideo;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.Map;

/**
 * 제휴 광고영상(6초 1컷, Kling) 엔드포인트 — 제휴 페이지의 독립 "AI 광고영상" 섹션이 쓴다.
 * 비동기: {@code POST}로 시작 → {@code status} 폴링 → 완료 시 {@code output}으로 mp4 서빙.
 */
@RestController
@RequestMapping("/ai/affiliate/video")
public class AffiliateVideoController {

    private final AffiliateVideoService service;

    public AffiliateVideoController(AffiliateVideoService service) {
        this.service = service;
    }

    @PostMapping
    public AffiliateVideoDtos.SubmitResponse submit(@AuthenticationPrincipal Long userId,
                                                    @RequestBody AffiliateVideoDtos.SubmitRequest request) {
        return service.submit(userId, request);
    }

    @GetMapping("/{jobId}/status")
    public AffiliateVideoDtos.StatusResponse status(@AuthenticationPrincipal Long userId,
                                                    @PathVariable String jobId) {
        return service.status(userId, jobId);
    }

    @GetMapping("/{jobId}/output")
    public ResponseEntity<Resource> output(@AuthenticationPrincipal Long userId, @PathVariable String jobId) {
        Path file = service.outputFile(userId, jobId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(new FileSystemResource(file));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", e.getMessage()));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleArg(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
}
