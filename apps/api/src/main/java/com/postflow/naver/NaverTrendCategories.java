package com.postflow.naver;

import java.util.List;

/**
 * 제휴 발굴용 카테고리 → 후보 키워드 세트(하이브리드 발굴의 시드). 네이버 오픈API가 "카테고리 top
 * 급상승 키워드"를 통째로 주진 않으므로, 여기 후보를 네이버 쇼핑인사이트 트렌드로 "뜨는 순" 정렬해
 * 보여준다. {@code code}는 네이버 쇼핑 분야코드(클릭 데이터 스코프). 키워드는 분야당 최대 5개
 * (DataLab category/keywords 한 요청 제한).
 */
public final class NaverTrendCategories {

    private NaverTrendCategories() {
    }

    public record Cat(String key, String label, String code, List<String> keywords) {
    }

    public static final List<Cat> ALL = List.of(
            new Cat("summer", "여름가전", "50000003", List.of("선풍기", "서큘레이터", "제습기", "냉풍기", "에어컨")),
            new Cat("kitchen", "주방가전", "50000003", List.of("에어프라이어", "커피머신", "전기밥솥", "블렌더", "인덕션")),
            new Cat("beauty", "뷰티", "50000002", List.of("선크림", "세럼", "클렌징폼", "마스크팩", "립틴트")),
            new Cat("living", "홈/리빙", "50000004", List.of("무선청소기", "로봇청소기", "가습기", "스탠드조명", "수납장")),
            new Cat("health", "건강/운동", "50000008", List.of("단백질보충제", "요가매트", "덤벨", "폼롤러", "러닝화")),
            new Cat("baby", "육아", "50000005", List.of("기저귀", "물티슈", "유아식기", "젖병", "아기침대")),
            new Cat("digital", "디지털", "50000003", List.of("무선이어폰", "보조배터리", "블루투스스피커", "노트북거치대", "웹캠"))
    );

    public static Cat byKey(String key) {
        return ALL.stream().filter(c -> c.key().equals(key)).findFirst().orElse(null);
    }
}
