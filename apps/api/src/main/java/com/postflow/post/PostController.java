package com.postflow.post;

import com.postflow.post.dto.CreatePostRequest;
import com.postflow.post.dto.ImprovementDto;
import com.postflow.post.dto.PostDto;
import com.postflow.post.dto.UpdatePostRequest;
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

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping
    public List<PostDto> list(@AuthenticationPrincipal Long userId) {
        return postService.list(userId);
    }

    @GetMapping("/improvements")
    public List<ImprovementDto> improvements(@AuthenticationPrincipal Long userId,
                                             @org.springframework.web.bind.annotation.RequestParam(defaultValue = "60") int threshold) {
        return postService.improvements(userId, threshold);
    }

    @GetMapping("/{id}")
    public PostDto get(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return postService.get(userId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostDto create(@AuthenticationPrincipal Long userId,
                          @Valid @RequestBody CreatePostRequest request) {
        return postService.create(userId, request);
    }

    @PutMapping("/{id}")
    public PostDto update(@AuthenticationPrincipal Long userId,
                          @PathVariable Long id,
                          @Valid @RequestBody UpdatePostRequest request) {
        return postService.update(userId, id, request);
    }

    public record MediaRequest(String mediaUrl) {
    }

    @PutMapping("/{id}/media")
    public PostDto setMedia(@AuthenticationPrincipal Long userId,
                            @PathVariable Long id,
                            @RequestBody MediaRequest request) {
        return postService.setMedia(userId, id, request.mediaUrl());
    }

    public record AccountRequest(Long socialAccountId) {
    }

    @PutMapping("/{id}/account")
    public PostDto setAccount(@AuthenticationPrincipal Long userId,
                              @PathVariable Long id,
                              @RequestBody AccountRequest request) {
        return postService.setAccount(userId, id, request.socialAccountId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        postService.delete(userId, id);
    }

    @PostMapping("/{id}/publish")
    public PostDto publishNow(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return postService.publishNow(userId, id);
    }
}
