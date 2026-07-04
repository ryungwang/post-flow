package com.postflow.threads.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 공개 Threads 프로필 조회 결과({@code GET /profile_lookup}). threads_profile_discovery.
 * 팔로워 100명 이상 공개 계정만. 지표는 최근 7일. 경쟁사/인플루언서 분석용.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ThreadsProfileLookup(
        String username,
        String name,
        String biography,
        @JsonProperty("profile_picture_url") String profilePictureUrl,
        @JsonProperty("is_verified") Boolean isVerified,
        @JsonProperty("follower_count") Long followerCount,
        @JsonProperty("likes_count") Long likesCount,
        @JsonProperty("reposts_count") Long repostsCount,
        @JsonProperty("quotes_count") Long quotesCount,
        @JsonProperty("views_count") Long viewsCount
) {
}
