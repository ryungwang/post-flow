package com.postflow.threads;

import com.postflow.post.Post;
import com.postflow.post.PostRepository;
import com.postflow.post.PostStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Cron that publishes due posts. Threads has no native scheduling, so we run our own loop:
 * pick SCHEDULED posts whose time has arrived, then create+publish the container now.
 * Failures bump retry_count and stay SCHEDULED until {@code MAX_RETRIES} (then FAILED).
 */
@Component
public class ScheduledPublisher {

    private static final Logger log = LoggerFactory.getLogger(ScheduledPublisher.class);

    private final PostRepository postRepository;
    private final PublishingProcessor processor;
    private final ThreadsPublishService publishService;

    public ScheduledPublisher(PostRepository postRepository,
                              PublishingProcessor processor,
                              ThreadsPublishService publishService) {
        this.postRepository = postRepository;
        this.processor = processor;
        this.publishService = publishService;
    }

    @Scheduled(fixedDelayString = "${threads.publisher.interval-ms:60000}")
    public void publishDuePosts() {
        List<Post> due = postRepository.findByStatusAndScheduledAtLessThanEqual(
                PostStatus.SCHEDULED, Instant.now());
        if (due.isEmpty()) {
            return;
        }
        log.info("Publishing {} due post(s)", due.size());
        for (Post post : due) {
            Long postId = post.getId();
            processor.claim(postId).ifPresent(task -> {
                try {
                    String mediaId = publishService.publish(
                            task.threadsUserId(), task.accessToken(), task.content(), task.mediaUrl());
                    processor.complete(postId, mediaId);
                } catch (ThreadsApiException e) {
                    log.warn("Publish failed for post {}: {}", postId, e.getMessage());
                    processor.fail(postId, e.getMessage());
                }
            });
        }
    }
}
