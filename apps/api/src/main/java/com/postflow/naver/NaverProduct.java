package com.postflow.naver;

/**
 * 정리된 네이버 쇼핑 상품(제휴 콘텐츠 근거용). {@code price}·{@code image}는 네이버 쇼핑 기준이라
 * 쿠팡과 다를 수 있어 <b>참고용</b>이다(본문 자동 삽입 X — 프론트에서 참고 표시만).
 *
 * @param name       제품명(태그 제거)
 * @param brand      브랜드
 * @param maker      제조사
 * @param category   카테고리 경로(A > B > C)
 * @param price      최저가(원) — 참고용, nullable
 * @param image      이미지 URL — 참고용
 * @param link       네이버 쇼핑 링크(쿠팡 링크 아님)
 * @param mallName   판매처
 * @param productId  네이버 상품 id
 */
public record NaverProduct(
        String name,
        String brand,
        String maker,
        String category,
        Integer price,
        String image,
        String link,
        String mallName,
        String productId
) {
}
