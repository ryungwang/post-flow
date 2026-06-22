package com.postflow.post;

import com.postflow.ai.content.ContentScorer;
import com.postflow.post.dto.CreatePostRequest;
import com.postflow.post.dto.ImprovementDto;
import com.postflow.post.dto.PostDto;
import com.postflow.post.dto.UpdatePostRequest;
import com.postflow.threads.PublishingProcessor;
import com.postflow.threads.PublishingProcessor.PublishTask;
import com.postflow.threads.ThreadsApiException;
import com.postflow.threads.ThreadsPublishService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final PublishingProcessor publishingProcessor;
    private final ThreadsPublishService publishService;
    private final com.postflow.user.UsageService usageService;
    private final com.postflow.social.SocialAccountRepository socialAccountRepository;

    public PostService(PostRepository postRepository,
                       PublishingProcessor publishingProcessor,
                       ThreadsPublishService publishService,
                       com.postflow.user.UsageService usageService,
                       com.postflow.social.SocialAccountRepository socialAccountRepository) {
        this.postRepository = postRepository;
        this.publishingProcessor = publishingProcessor;
        this.publishService = publishService;
        this.usageService = usageService;
        this.socialAccountRepository = socialAccountRepository;
    }

    @Transactional(readOnly = true)
    public List<PostDto> list(Long userId) {
        return postRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(PostDto::from).toList();
    }

    @Transactional(readOnly = true)
    public PostDto get(Long userId, Long id) {
        return PostDto.from(loadOwned(userId, id));
    }

    /** Posts scoring below {@code threshold}, lowest first, each with top improvement tips. */
    @Transactional(readOnly = true)
    public List<ImprovementDto> improvements(Long userId, int threshold) {
        return postRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(p -> {
                    var a = ContentScorer.analyze(p.getContent(), p.getHashtags(), p.getCta());
                    return new ImprovementDto(p.getId(), p.getContent(), a.total(),
                            p.getStatus().name(), a.tips().stream().limit(3).toList());
                })
                .filter(d -> d.score() < threshold)
                .sorted(java.util.Comparator.comparingInt(ImprovementDto::score))
                .limit(10)
                .toList();
    }

    @Transactional
    public PostDto create(Long userId, CreatePostRequest request) {
        Post post = Post.create(userId, request.content(), request.hashtags(), request.cta(), request.mediaUrl());
        if (request.scheduledAt() != null) {
            usageService.assertCanSchedule(userId);
            post.schedule(request.scheduledAt());
        }
        return PostDto.from(postRepository.save(post));
    }

    @Transactional
    public PostDto setMedia(Long userId, Long id, String mediaUrl) {
        Post post = loadOwned(userId, id);
        post.updateMedia(mediaUrl);
        return PostDto.from(post);
    }

    @Transactional
    public PostDto setAccount(Long userId, Long id, Long socialAccountId) {
        Post post = loadOwned(userId, id);
        if (socialAccountId != null) {
            socialAccountRepository.findById(socialAccountId)
                    .filter(a -> a.getUserId().equals(userId))
                    .orElseThrow(() -> new IllegalArgumentException("account not found"));
        }
        post.updateSocialAccount(socialAccountId);
        return PostDto.from(post);
    }

    @Transactional
    public PostDto update(Long userId, Long id, UpdatePostRequest request) {
        Post post = loadOwned(userId, id);
        post.updateContent(request.content());
        post.updateMeta(request.hashtags(), request.cta());
        if (request.scheduledAt() != null) {
            usageService.assertCanSchedule(userId);
            post.schedule(request.scheduledAt());
        }
        return PostDto.from(post);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Post post = loadOwned(userId, id);
        postRepository.delete(post);
    }

    /**
     * Publish immediately. Ownership is checked first; the actual publish runs outside a
     * transaction (external HTTP + polling) via the shared {@link PublishingProcessor}.
     */
    public PostDto publishNow(Long userId, Long id) {
        loadOwned(userId, id); // ownership / existence guard
        Optional<PublishTask> claimed = publishingProcessor.claimImmediate(id);
        if (claimed.isEmpty()) {
            // not publishable (e.g. token expired → marked RECONNECT_REQUIRED, or wrong state)
            return PostDto.from(reload(id));
        }
        PublishTask task = claimed.get();
        try {
            String mediaId = publishService.publish(
                    task.threadsUserId(), task.accessToken(), task.content(), task.mediaUrl());
            publishingProcessor.complete(id, mediaId);
        } catch (ThreadsApiException e) {
            publishingProcessor.fail(id, e.getMessage());
        }
        return PostDto.from(reload(id));
    }

    private Post loadOwned(Long userId, Long id) {
        Post post = postRepository.findById(id).orElseThrow(() -> new PostNotFoundException(id));
        if (!post.getUserId().equals(userId)) {
            // treat as not-found to avoid leaking existence
            throw new PostNotFoundException(id);
        }
        return post;
    }

    private Post reload(Long id) {
        return postRepository.findById(id).orElseThrow(() -> new PostNotFoundException(id));
    }
}
