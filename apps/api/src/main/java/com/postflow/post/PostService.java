package com.postflow.post;

import com.postflow.ai.content.ContentScorer;
import com.postflow.post.dto.CreatePostRequest;
import com.postflow.post.dto.ImprovementDto;
import com.postflow.post.dto.PostDto;
import com.postflow.post.dto.PostTargetDto;
import com.postflow.post.dto.UpdatePostRequest;
import com.postflow.social.PublishException;
import com.postflow.social.PublisherRegistry;
import com.postflow.social.SocialAccount;
import com.postflow.social.SocialAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PostService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PostService.class);

    private final PostRepository postRepository;
    private final PostTargetRepository targetRepository;
    private final TargetPublishingProcessor targetProcessor;
    private final PublisherRegistry publisherRegistry;
    private final com.postflow.user.UsageService usageService;
    private final SocialAccountRepository socialAccountRepository;

    public PostService(PostRepository postRepository,
                       PostTargetRepository targetRepository,
                       TargetPublishingProcessor targetProcessor,
                       PublisherRegistry publisherRegistry,
                       com.postflow.user.UsageService usageService,
                       SocialAccountRepository socialAccountRepository) {
        this.postRepository = postRepository;
        this.targetRepository = targetRepository;
        this.targetProcessor = targetProcessor;
        this.publisherRegistry = publisherRegistry;
        this.usageService = usageService;
        this.socialAccountRepository = socialAccountRepository;
    }

    @Transactional(readOnly = true)
    public List<PostDto> list(Long userId) {
        return toDtos(postRepository.findByUserIdOrderByCreatedAtDesc(userId));
    }

    @Transactional(readOnly = true)
    public PostDto get(Long userId, Long id) {
        return toDto(loadOwned(userId, id));
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
        postRepository.save(post); // need id before creating targets
        applyChannels(userId, post, request.channelIds());
        return toDto(post);
    }

    @Transactional
    public PostDto setMedia(Long userId, Long id, String mediaUrl) {
        Post post = loadOwned(userId, id);
        post.updateMedia(mediaUrl);
        return toDto(post);
    }

    /** Replace the post's publish channels (fan-out targets). Empty = no channels. */
    @Transactional
    public PostDto setChannels(Long userId, Long id, List<Long> channelIds) {
        Post post = loadOwned(userId, id);
        applyChannels(userId, post, channelIds);
        return toDto(post);
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
        return toDto(post);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Post post = loadOwned(userId, id);
        // best-effort: remove already-published records from each platform (Threads/Bluesky).
        for (PostTarget t : targetRepository.findByPostIdOrderByIdAsc(id)) {
            if (t.getStatus() == PostTargetStatus.PUBLISHED && t.getPlatformPostId() != null) {
                socialAccountRepository.findById(t.getSocialAccountId()).ifPresent(acc -> {
                    try {
                        publisherRegistry.get(acc.getProvider()).deletePost(acc.getId(), t.getPlatformPostId());
                    } catch (RuntimeException e) {
                        log.warn("Platform delete failed (post {} target {}): {}", id, t.getId(), e.getMessage());
                    }
                });
            }
        }
        postRepository.delete(post); // post_targets cascade-deleted (FK ON DELETE CASCADE)
    }

    /**
     * Publish immediately by fanning out to every pending channel target. Each target is
     * published independently (partial failure allowed). External HTTP runs outside a txn;
     * per-target state transitions go through {@link TargetPublishingProcessor}.
     */
    public PostDto publishNow(Long userId, Long id) {
        loadOwned(userId, id); // ownership / existence guard
        for (Long targetId : targetProcessor.pendingTargetIds(id)) {
            targetProcessor.claim(targetId).ifPresent(task -> {
                try {
                    String platformPostId = publisherRegistry.get(task.provider())
                            .publish(task.accountId(), task.content(), task.mediaUrl());
                    targetProcessor.complete(targetId, platformPostId);
                } catch (PublishException e) {
                    targetProcessor.fail(targetId, e.getMessage());
                }
            });
        }
        return toDto(reload(id));
    }

    /** Replace a post's targets with the given channels (validated as owned). */
    private void applyChannels(Long userId, Post post, List<Long> channelIds) {
        targetRepository.deleteByPostId(post.getId());
        targetRepository.flush();
        List<Long> ids = (channelIds != null && !channelIds.isEmpty())
                ? channelIds
                : defaultChannelIds(userId);
        for (Long accountId : ids) {
            SocialAccount account = socialAccountRepository.findById(accountId)
                    .filter(a -> a.getUserId().equals(userId))
                    .orElseThrow(() -> new IllegalArgumentException("channel not found: " + accountId));
            targetRepository.save(PostTarget.create(post.getId(), account.getId()));
        }
    }

    /** Fallback when no channels are given: the user's default channel (else first, else none). */
    private List<Long> defaultChannelIds(Long userId) {
        return socialAccountRepository.findFirstByUserIdAndIsDefaultTrue(userId)
                .map(a -> List.of(a.getId()))
                .orElseGet(() -> socialAccountRepository.findByUserIdOrderByIdAsc(userId).stream()
                        .findFirst().map(a -> List.of(a.getId())).orElse(List.of()));
    }

    // ── DTO enrichment (targets need provider/handle from SocialAccount) ──

    private PostDto toDto(Post post) {
        List<PostTarget> targets = targetRepository.findByPostIdOrderByIdAsc(post.getId());
        return PostDto.from(post, targetDtos(targets, accountsFor(targets)));
    }

    private List<PostDto> toDtos(List<Post> posts) {
        if (posts.isEmpty()) {
            return List.of();
        }
        List<Long> postIds = posts.stream().map(Post::getId).toList();
        Map<Long, List<PostTarget>> byPost = targetRepository.findByPostIdInOrderByIdAsc(postIds).stream()
                .collect(Collectors.groupingBy(PostTarget::getPostId));
        List<PostTarget> all = byPost.values().stream().flatMap(List::stream).toList();
        Map<Long, SocialAccount> accById = accountsFor(all);
        return posts.stream()
                .map(p -> PostDto.from(p, targetDtos(byPost.getOrDefault(p.getId(), List.of()), accById)))
                .toList();
    }

    private Map<Long, SocialAccount> accountsFor(List<PostTarget> targets) {
        List<Long> accIds = targets.stream().map(PostTarget::getSocialAccountId).distinct().toList();
        Map<Long, SocialAccount> map = new LinkedHashMap<>();
        socialAccountRepository.findAllById(accIds).forEach(a -> map.put(a.getId(), a));
        return map;
    }

    private List<PostTargetDto> targetDtos(List<PostTarget> targets, Map<Long, SocialAccount> accById) {
        return targets.stream().map(t -> {
            SocialAccount a = accById.get(t.getSocialAccountId());
            String channel = a == null ? null : (a.getUsername() != null ? a.getUsername() : a.getExternalId());
            return new PostTargetDto(
                    t.getSocialAccountId(),
                    a == null ? null : a.getProvider().name(),
                    channel,
                    t.getStatus().name(),
                    t.getPlatformPostId(),
                    t.getErrorMessage());
        }).toList();
    }

    private Post loadOwned(Long userId, Long id) {
        Post post = postRepository.findById(id).orElseThrow(() -> new PostNotFoundException(id));
        if (!post.getUserId().equals(userId)) {
            throw new PostNotFoundException(id); // treat as not-found (don't leak existence)
        }
        return post;
    }

    private Post reload(Long id) {
        return postRepository.findById(id).orElseThrow(() -> new PostNotFoundException(id));
    }
}
