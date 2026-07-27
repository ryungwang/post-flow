package com.postflow.naver;

/**
 * 카테고리 내 후보 키워드의 최근 트렌드 요약.
 *
 * @param keyword 키워드
 * @param growth  최근 상승률(%) — 기간 첫 주 대비 마지막 주 클릭지수 변화. 급상승 정렬 기준.
 * @param score   최신 관심도 지수(요청 내 상대값, 0~100)
 */
public record RisingKeyword(String keyword, double growth, double score) {
}
