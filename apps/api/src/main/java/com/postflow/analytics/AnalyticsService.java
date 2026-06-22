package com.postflow.analytics;

import com.postflow.analytics.dto.AnalyticsDashboardResponse;
import com.postflow.analytics.dto.TopPostDto;
import com.postflow.post.Post;
import com.postflow.post.PostRepository;
import com.postflow.post.PostStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates analytics from the user's posts + their metric snapshots. Metric data is
 * populated by Threads insight collection; with none collected the totals are simply 0.
 */
@Service
public class AnalyticsService {

    private final PostRepository postRepository;
    private final AnalyticsRepository analyticsRepository;

    public AnalyticsService(PostRepository postRepository, AnalyticsRepository analyticsRepository) {
        this.postRepository = postRepository;
        this.analyticsRepository = analyticsRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsDashboardResponse dashboard(Long userId) {
        List<Post> posts = postRepository.findByUserIdOrderByCreatedAtDesc(userId);
        long total = posts.size();
        long published = posts.stream().filter(p -> p.getStatus() == PostStatus.PUBLISHED).count();
        long scheduled = posts.stream().filter(p -> p.getStatus() == PostStatus.SCHEDULED).count();

        List<Long> ids = posts.stream().map(Post::getId).toList();
        List<PostAnalytics> rows = ids.isEmpty() ? List.of() : analyticsRepository.findByPostIdIn(ids);

        long views = rows.stream().mapToLong(PostAnalytics::getViews).sum();
        long likes = rows.stream().mapToLong(PostAnalytics::getLikes).sum();
        long replies = rows.stream().mapToLong(PostAnalytics::getReplies).sum();
        long reposts = rows.stream().mapToLong(PostAnalytics::getReposts).sum();
        long quotes = rows.stream().mapToLong(PostAnalytics::getQuotes).sum();
        long shares = rows.stream().mapToLong(PostAnalytics::getShares).sum();
        double engagementRate = views > 0 ? (double) (likes + replies + reposts + quotes) / views : 0.0;

        Map<Long, PostAnalytics> byPost = new HashMap<>();
        for (PostAnalytics a : rows) {
            byPost.put(a.getPostId(), a);
        }
        List<TopPostDto> topPosts = posts.stream()
                .map(p -> {
                    PostAnalytics a = byPost.get(p.getId());
                    long v = a != null ? a.getViews() : 0;
                    return new TopPostDto(p.getId(), p.getContent(), v,
                            a != null ? a.getLikes() : 0, a != null ? a.getReplies() : 0);
                })
                .sorted(Comparator.comparingLong(TopPostDto::views).reversed())
                .limit(5)
                .toList();

        return new AnalyticsDashboardResponse(
                total, published, scheduled, views, likes, replies, reposts, quotes, shares,
                engagementRate, topPosts);
    }
}
