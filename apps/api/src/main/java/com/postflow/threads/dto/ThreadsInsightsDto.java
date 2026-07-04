package com.postflow.threads.dto;

import com.postflow.threads.api.ThreadsDemographics.Entry;

import java.util.List;

/**
 * 계정 인사이트: 팔로워 수 + 팔로워 인구통계(연령/성별/국가/도시).
 * 게시물별 지표·총계·랭킹은 {@code /threads/posts}(게시물+지표)에서 프론트가 파생.
 */
public record ThreadsInsightsDto(
        Long followers,
        Demographics demographics
) {
    public record Demographics(
            List<Entry> age,
            List<Entry> gender,
            List<Entry> country,
            List<Entry> city
    ) {
    }
}
