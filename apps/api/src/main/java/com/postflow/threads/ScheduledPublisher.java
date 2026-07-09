package com.postflow.threads;

import com.postflow.post.Post;
import com.postflow.post.PostRepository;
import com.postflow.post.PostStatus;
import com.postflow.post.TargetPublishingProcessor;
import com.postflow.social.PublishException;
import com.postflow.social.PublisherRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Cron that publishes due posts. No native scheduling on Threads/Bluesky, so we run our own
 * loop: pick SCHEDULED posts whose time has arrived, then fan out to each pending channel
 * target now. Per-target failures retry (bounded) via {@link TargetPublishingProcessor};
 * a post stays SCHEDULED while any target is still pending, else aggregates to PUBLISHED/
 * PARTIAL/FAILED.
 */
@Component
public class ScheduledPublisher {

    private static final Logger log = LoggerFactory.getLogger(ScheduledPublisher.class);

    private final PostRepository postRepository;
    private final TargetPublishingProcessor targetProcessor;
    private final PublisherRegistry publisherRegistry;

    public ScheduledPublisher(PostRepository postRepository,
                              TargetPublishingProcessor targetProcessor,
                              PublisherRegistry publisherRegistry) {
        this.postRepository = postRepository;
        this.targetProcessor = targetProcessor;
        this.publisherRegistry = publisherRegistry;
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
            for (Long targetId : targetProcessor.pendingTargetIds(post.getId())) {
                targetProcessor.claim(targetId).ifPresent(task -> {
                    try {
                        String platformPostId = publisherRegistry.get(task.provider())
                                .publish(task.accountId(), task.content(), task.mediaUrl());
                        targetProcessor.complete(targetId, platformPostId);
                    } catch (PublishException e) {
                        log.warn("Publish failed for post {} target {}: {}",
                                post.getId(), targetId, e.getMessage());
                        targetProcessor.fail(targetId, e.getMessage());
                    }
                });
            }
        }
    }
}
