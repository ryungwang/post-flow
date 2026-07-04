package com.postflow.threads.dto;

import java.util.List;

/**
 * 요일별 평균 참여율(기간 조회). weekday는 JS getDay와 동일(0=일 … 6=토), KST 기준.
 * days=조회 기간(일), sampled=실제 집계에 쓴 게시물 수.
 */
public record DayEngagementDto(int days, int sampled, List<DayStat> stats) {
    public record DayStat(int weekday, double avgEngagement, int count) {
    }
}
