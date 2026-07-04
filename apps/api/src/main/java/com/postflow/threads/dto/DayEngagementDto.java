package com.postflow.threads.dto;

import java.util.List;

/**
 * 요일별/일별 평균 참여율(기간 조회). KST 기준.
 * stats: 요일별(weekday 0=일..6=토). daily: 날짜별 연속 구간(빈 날 포함, 최신 뒤로).
 * sampled=집계에 쓴 게시물 수.
 */
public record DayEngagementDto(int days, int sampled, List<DayStat> stats, List<DateStat> daily) {
    public record DayStat(int weekday, double avgEngagement, int count) {
    }

    public record DateStat(String date, double avgEngagement, int count) {
    }
}
