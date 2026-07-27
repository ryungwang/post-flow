package com.postflow.naver;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 상품 레이더 — 카테고리별 후보를 네이버 DataLab(검색어트렌드 + 쇼핑인사이트)로 점수화해 급상승 순으로 돌려준다.
 *
 * <p>⚠️ 쿠팡 API 승인 전이라 가격·할인·로켓배송은 <b>'확인 불가'</b>로 두고 <b>가용 항목만으로 정규화</b>한다
 * (0점 처리 금지). 총점 100 배분: 검색 추이 30 · 쇼핑 검색 25 · 계절 25 · 카테고리 적합 15
 * · (할인 10 · 로켓 10 · 가격대 10 = 확인 불가).
 */
@Service
public class ProductRadarService {

    private final NaverDataLabClient dataLab;

    public ProductRadarService(NaverDataLabClient dataLab) {
        this.dataLab = dataLab;
    }

    /** DataLab 사용 가능 여부 — 프론트에 '추이 확인 불가' 안내용. */
    public boolean dataLabConfigured() {
        return dataLab.configured();
    }

    /** 후보 키워드 + 계절 피크월. peakMonths 비면 사철(중립). */
    private record Candidate(String name, List<Integer> peakMonths) {
        Candidate(String name) {
            this(name, List.of());
        }
    }

    /** 기본 카테고리 → 후보. 여름 피크 품목엔 peakMonths 표기. */
    private static final Map<String, List<Candidate>> CATALOG = new LinkedHashMap<>();

    static {
        CATALOG.put("living", List.of(
                new Candidate("제습기", List.of(6, 7, 8)), new Candidate("선풍기", List.of(6, 7, 8)),
                new Candidate("모기퇴치기", List.of(6, 7, 8)), new Candidate("물티슈"),
                new Candidate("세탁세제"), new Candidate("제모기"), new Candidate("무선청소기"),
                new Candidate("빨래건조대"), new Candidate("탈취제"), new Candidate("수납정리함")));
        CATALOG.put("digital", List.of(
                new Candidate("무선이어폰"), new Candidate("로봇청소기"), new Candidate("에어컨", List.of(6, 7, 8)),
                new Candidate("보조배터리"), new Candidate("블루투스스피커"), new Candidate("게이밍마우스"),
                new Candidate("노트북거치대"), new Candidate("휴대용선풍기", List.of(6, 7, 8)),
                new Candidate("모니터암"), new Candidate("USB허브")));
        CATALOG.put("kitchen", List.of(
                new Candidate("에어프라이어"), new Candidate("텀블러"), new Candidate("밀폐용기"),
                new Candidate("커피머신"), new Candidate("제빙기", List.of(6, 7, 8)),
                new Candidate("전기포트"), new Candidate("도마"), new Candidate("실리콘주방용품"),
                new Candidate("보온병"), new Candidate("냉장고정리")));
        CATALOG.put("beauty", List.of(
                new Candidate("선크림", List.of(5, 6, 7, 8)), new Candidate("쿠션팩트"),
                new Candidate("클렌징오일"), new Candidate("헤어드라이어"), new Candidate("향수"),
                new Candidate("수분크림"), new Candidate("립밤"), new Candidate("제모크림", List.of(6, 7, 8)),
                new Candidate("바디워시"), new Candidate("마스크팩")));
        CATALOG.put("travel", List.of(
                new Candidate("캐리어", List.of(7, 8)), new Candidate("캠핑텐트", List.of(5, 6, 7, 8, 9)),
                new Candidate("아이스박스", List.of(6, 7, 8)), new Candidate("물놀이튜브", List.of(7, 8)),
                new Candidate("캠핑의자", List.of(5, 6, 7, 8, 9)), new Candidate("여행용파우치"),
                new Candidate("목베개"), new Candidate("돗자리", List.of(4, 5, 6, 7, 8, 9)),
                new Candidate("휴대용선풍기", List.of(6, 7, 8)), new Candidate("래시가드", List.of(6, 7, 8))));
    }

    public static final List<Map<String, String>> CATEGORIES = List.of(
            Map.of("key", "living", "label", "생활용품"),
            Map.of("key", "digital", "label", "디지털·가전"),
            Map.of("key", "kitchen", "label", "주방용품"),
            Map.of("key", "beauty", "label", "뷰티"),
            Map.of("key", "travel", "label", "여행·계절용품"));

    /** 레이더 카테고리 → 네이버쇼핑 분야 코드(cid). 쇼핑인사이트 조회용. */
    private static final Map<String, String> SHOPPING_CID = Map.of(
            "living", "50000008",   // 생활/건강
            "digital", "50000003",  // 디지털/가전
            "kitchen", "50000008",  // 생활/건강(주방용품)
            "beauty", "50000002",   // 화장품/미용
            "travel", "50000007");  // 스포츠/레저

    /** 점수 근거 한 항목. status: available | unavailable(확인 불가). */
    public record ScoreItem(String label, Integer score, int max, String status, String note) {
    }

    /** 점수화된 상품 후보. price/discount/rocket 은 쿠팡 승인 전까지 확인 불가. */
    public record ScoredProduct(String name, String category, int score, Double riseRate,
                                List<Double> trend, List<ScoreItem> breakdown) {
    }

    /**
     * @param categoryKey living/digital/kitchen/beauty/travel
     * @param windowDays  7 또는 30 (추이 비교 구간)
     */
    public List<ScoredProduct> radar(String categoryKey, int windowDays) {
        List<Candidate> cands = CATALOG.getOrDefault(categoryKey, List.of());
        if (cands.isEmpty()) {
            return List.of();
        }
        int win = windowDays >= 30 ? 30 : 14; // 7일 비교엔 14일 데이터, 30일 비교엔 30일
        LocalDate end = LocalDate.now().minusDays(1); // DataLab 은 전일까지
        LocalDate start = end.minusDays(win - 1);
        int month = end.getMonthValue();

        List<String> names = cands.stream().map(Candidate::name).toList();
        Map<String, Double> rises = dataLab.riseRates(names, start, end);
        Map<String, Double> shopRises = dataLab.shoppingRiseRates(SHOPPING_CID.get(categoryKey), names, start, end);

        List<ScoredProduct> out = new ArrayList<>();
        for (Candidate c : cands) {
            out.add(score(c, categoryKey, rises.get(c.name()), shopRises.get(c.name()), month));
        }
        out.sort((a, b) -> Integer.compare(b.score(), a.score()));
        return out;
    }

    private ScoredProduct score(Candidate c, String categoryKey, Double rise, Double shopRise, int month) {
        List<ScoreItem> items = new ArrayList<>();
        double earned = 0;
        double avail = 0;

        // 1) 검색 추이 30 — rise% 를 0~30 으로(+50%→30, 0%→15, -50%→0). 데이터 없으면 확인 불가.
        if (rise != null) {
            int s = (int) Math.round(clamp(15 + rise / 50.0 * 15, 0, 30));
            items.add(new ScoreItem("검색 추이 상승률", s, 30, "available",
                    String.format("최근 대비 %+.0f%%", rise)));
            earned += s;
            avail += 30;
        } else {
            items.add(new ScoreItem("검색 추이 상승률", null, 30, "unavailable", "트렌드 데이터 없음"));
        }

        // 1-2) 쇼핑 검색 25 — 쇼핑인사이트(구매 의도). 검색어트렌드와 같은 방식으로 25점 배분.
        if (shopRise != null) {
            int s = (int) Math.round(clamp(12.5 + shopRise / 50.0 * 12.5, 0, 25));
            items.add(new ScoreItem("쇼핑 검색 상승률", s, 25, "available",
                    String.format("네이버쇼핑 최근 대비 %+.0f%%", shopRise)));
            earned += s;
            avail += 25;
        } else {
            items.add(new ScoreItem("쇼핑 검색 상승률", null, 25, "unavailable", "쇼핑인사이트 데이터 없음"));
        }

        // 2) 계절 적합 25 — 현재 월이 피크면 25, 인접월 14, 사철 12, 비수기 4.
        int seasonScore;
        String seasonNote;
        if (c.peakMonths().isEmpty()) {
            seasonScore = 12;
            seasonNote = "사철 상품";
        } else if (c.peakMonths().contains(month)) {
            seasonScore = 25;
            seasonNote = month + "월 성수기";
        } else if (c.peakMonths().contains(month - 1) || c.peakMonths().contains(month + 1)) {
            seasonScore = 14;
            seasonNote = "성수기 인접";
        } else {
            seasonScore = 4;
            seasonNote = "비수기";
        }
        items.add(new ScoreItem("계절·시기 적합성", seasonScore, 25, "available", seasonNote));
        earned += seasonScore;
        avail += 25;

        // 3) 카테고리 적합 15
        items.add(new ScoreItem("카테고리 적합성", 15, 15, "available", categoryLabel(categoryKey) + " 카테고리"));
        earned += 15;
        avail += 15;

        // 4~6) 할인·로켓·가격대 = 쿠팡 승인 전까지 확인 불가 → 정규화 분모에서 제외.
        items.add(new ScoreItem("할인 여부", null, 10, "unavailable", "쿠팡 연동 후 확인"));
        items.add(new ScoreItem("로켓배송", null, 10, "unavailable", "쿠팡 연동 후 확인"));
        items.add(new ScoreItem("가격대", null, 10, "unavailable", "쿠팡 연동 후 확인"));

        int normalized = avail > 0 ? (int) Math.round(earned / avail * 100) : 0;
        return new ScoredProduct(c.name(), categoryKey, normalized, rise, List.of(), items);
    }

    private static String categoryLabel(String key) {
        return CATEGORIES.stream().filter(m -> m.get("key").equals(key)).findFirst()
                .map(m -> m.get("label")).orElse(key);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
