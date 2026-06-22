package com.postflow.post;

import com.postflow.post.dto.CreatePostRequest;
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

    public PostService(PostRepository postRepository,
                       PublishingProcessor publishingProcessor,
                       ThreadsPublishService publishService) {
        this.postRepository = postRepository;
        this.publishingProcessor = publishingProcessor;
        this.publishService = publishService;
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

    @Transactional
    public PostDto create(Long userId, CreatePostRequest request) {
        Post post = Post.create(userId, request.content());
        if (request.scheduledAt() != null) {
            post.schedule(request.scheduledAt());
        }
        return PostDto.from(postRepository.save(post));
    }

    @Transactional
    public PostDto update(Long userId, Long id, UpdatePostRequest request) {
        Post post = loadOwned(userId, id);
        post.updateContent(request.content());
        if (request.scheduledAt() != null) {
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
            String mediaId = publishService.publishText(
                    task.threadsUserId(), task.accessToken(), task.content());
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
