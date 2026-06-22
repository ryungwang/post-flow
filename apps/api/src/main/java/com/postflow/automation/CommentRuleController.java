package com.postflow.automation;

import com.postflow.automation.CommentRuleService.TestResult;
import com.postflow.automation.dto.CommentRuleDto;
import com.postflow.automation.dto.CommentRuleRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/comment-rules")
public class CommentRuleController {

    private final CommentRuleService service;

    public CommentRuleController(CommentRuleService service) {
        this.service = service;
    }

    @GetMapping
    public List<CommentRuleDto> list(@AuthenticationPrincipal Long userId) {
        return service.list(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommentRuleDto create(@AuthenticationPrincipal Long userId, @Valid @RequestBody CommentRuleRequest req) {
        return service.create(userId, req);
    }

    @PutMapping("/{id}")
    public CommentRuleDto update(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                 @RequestBody CommentRuleRequest req) {
        return service.update(userId, id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        service.delete(userId, id);
    }

    @PostMapping("/{id}/test")
    public TestResult test(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                           @RequestBody Map<String, String> body) {
        return service.test(userId, id, body.getOrDefault("comment", ""));
    }
}
