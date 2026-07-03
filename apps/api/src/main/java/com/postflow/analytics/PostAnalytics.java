package com.postflow.analytics;

import com.postflow.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Per-post metrics snapshot. Field names follow the Threads Insights API:
 * {@code replies} (not "comments"); {@code engagementRate} is computed
 * (API does not provide it). See PRD → Analytics.
 */
@Getter
@Entity
@Table(name = "analytics")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostAnalytics extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(nullable = false)
    private long views = 0;

    @Column(nullable = false)
    private long likes = 0;

    @Column(nullable = false)
    private long replies = 0;

    @Column(nullable = false)
    private long reposts = 0;

    @Column(nullable = false)
    private long quotes = 0;

    @Column(nullable = false)
    private long shares = 0;

    @Column(name = "engagement_rate", nullable = false)
    private double engagementRate = 0.0;

    /** Seed/snapshot factory — engagementRate = (likes+replies+reposts+quotes)/views. */
    public static PostAnalytics of(Long postId, long views, long likes, long replies,
                                   long reposts, long quotes, long shares) {
        PostAnalytics a = new PostAnalytics();
        a.postId = postId;
        a.views = views;
        a.likes = likes;
        a.replies = replies;
        a.reposts = reposts;
        a.quotes = quotes;
        a.shares = shares;
        a.engagementRate = views > 0 ? (double) (likes + replies + reposts + quotes) / views : 0.0;
        return a;
    }
}
