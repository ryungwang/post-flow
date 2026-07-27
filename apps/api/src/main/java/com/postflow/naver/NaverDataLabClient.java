package com.postflow.naver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 네이버 DataLab — 상품 레이더의 상승률 근거. 검색어트렌드({@code /v1/datalab/search})와 쇼핑인사이트
 * ({@code /v1/datalab/shopping/category/keywords}) 둘 다 호출한다. 검색은 '얼마나 검색되나', 쇼핑은
 * '실제 쇼핑에서 얼마나 뜨나'(구매 의도에 가까움). 같은 오픈API 자격증명({@link NaverSearchProperties})
 * 재사용 — ⚠️ 네이버 앱에 <b>데이터랩(검색어트렌드 + 쇼핑인사이트) API 사용 설정</b>이 켜져 있어야 한다
 * (검색 API와 별개). 실제 트렌드 데이터만 근거로 쓰고, 데이터 없으면 그 키워드는 결과에서 빠진다('확인 불가').
 * 한 호출에 keyword group 최대 5개 → 5개씩 배치.
 */
@Component
public class NaverDataLabClient {

    private static final Logger log = LoggerFactory.getLogger(NaverDataLabClient.class);
    private static final int MAX_GROUPS = 5; // DataLab 제한

    private final NaverSearchProperties props;
    private final ObjectMapper mapper;
    private final RestClient http;

    public NaverDataLabClient(NaverSearchProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.http = RestClient.create(props.searchBaseUrlOrDefault());
    }

    public boolean configured() {
        return props.configured();
    }

    /** 키워드별 검색어트렌드 상승률(%). 데이터 없으면 그 키워드는 빠진다(호출부가 '확인 불가'로 처리). */
    public Map<String, Double> riseRates(List<String> keywords, LocalDate start, LocalDate end) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (!configured() || keywords == null || keywords.isEmpty()) {
            return out;
        }
        for (int i = 0; i < keywords.size(); i += MAX_GROUPS) {
            List<String> batch = keywords.subList(i, Math.min(i + MAX_GROUPS, keywords.size()));
            try {
                out.putAll(riseBatch(batch, start, end));
            } catch (Exception e) {
                log.warn("DataLab 검색 배치 실패(건너뜀): {}", e.getMessage());
            }
        }
        return out;
    }

    private Map<String, Double> riseBatch(List<String> batch, LocalDate start, LocalDate end) throws Exception {
        List<Map<String, Object>> groups = new ArrayList<>();
        for (String kw : batch) {
            groups.add(Map.of("groupName", kw, "keywords", List.of(kw)));
        }
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("startDate", start.toString());
        req.put("endDate", end.toString());
        req.put("timeUnit", "date");
        req.put("keywordGroups", groups);

        String body = http.post()
                .uri("/v1/datalab/search")
                .header("X-Naver-Client-Id", props.clientId())
                .header("X-Naver-Client-Secret", props.clientSecret())
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapper.writeValueAsString(req))
                .retrieve()
                .body(String.class);

        Map<String, Double> out = new LinkedHashMap<>();
        JsonNode results = mapper.readTree(body).path("results");
        for (JsonNode r : results) {
            String title = r.path("title").asText("");
            Double rise = riseFromDaily(r.path("data"), 8); // 검색어트렌드는 일별 촘촘 → 8포인트(7v7)
            if (!title.isBlank() && rise != null) {
                out.put(title, rise);
            }
        }
        return out;
    }

    /** 쇼핑인사이트 — 쇼핑 분야(cid) 내 키워드별 쇼핑 검색 상승률(%). 데이터 없으면 빠진다('확인 불가'). */
    public Map<String, Double> shoppingRiseRates(String categoryCid, List<String> keywords, LocalDate start, LocalDate end) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (!configured() || categoryCid == null || categoryCid.isBlank() || keywords == null || keywords.isEmpty()) {
            return out;
        }
        for (int i = 0; i < keywords.size(); i += MAX_GROUPS) {
            List<String> batch = keywords.subList(i, Math.min(i + MAX_GROUPS, keywords.size()));
            try {
                out.putAll(shoppingBatch(categoryCid, batch, start, end));
            } catch (Exception e) {
                log.warn("DataLab 쇼핑인사이트 배치 실패(건너뜀): {}", e.getMessage());
            }
        }
        return out;
    }

    private Map<String, Double> shoppingBatch(String cid, List<String> batch, LocalDate start, LocalDate end) throws Exception {
        List<Map<String, Object>> kw = new ArrayList<>();
        for (String k : batch) {
            kw.add(Map.of("name", k, "param", List.of(k)));
        }
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("startDate", start.toString());
        req.put("endDate", end.toString());
        req.put("timeUnit", "date");
        req.put("category", cid); // 단일 쇼핑 분야 코드
        req.put("keyword", kw);

        String body = http.post()
                .uri("/v1/datalab/shopping/category/keywords")
                .header("X-Naver-Client-Id", props.clientId())
                .header("X-Naver-Client-Secret", props.clientSecret())
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapper.writeValueAsString(req))
                .retrieve()
                .body(String.class);

        Map<String, Double> out = new LinkedHashMap<>();
        JsonNode results = mapper.readTree(body).path("results");
        for (JsonNode r : results) {
            String title = r.path("title").asText("");
            Double rise = riseFromDaily(r.path("data"), 4); // 쇼핑인사이트는 성겨 완화(4포인트, 2v2)
            if (!title.isBlank() && rise != null) {
                out.put(title, rise);
            }
        }
        return out;
    }

    /**
     * ratio 배열에서 (최근 절반 평균 - 이전 절반 평균) / 이전 절반 평균 * 100. {@code minSize} 미만이면 null.
     * 이전=0 & 최근>0 이면 +100(급상승). 검색어트렌드=8, 쇼핑인사이트=4.
     */
    private Double riseFromDaily(JsonNode data, int minSize) {
        if (data == null || !data.isArray() || data.size() < minSize) {
            return null;
        }
        int n = data.size();
        int half = n / 2;
        double recent = 0;
        double prev = 0;
        for (int i = 0; i < half; i++) {
            recent += data.get(n - 1 - i).path("ratio").asDouble(0);
            prev += data.get(i).path("ratio").asDouble(0);
        }
        recent /= half;
        prev /= half;
        if (prev <= 0.0001) {
            return recent > 0 ? 100.0 : null;
        }
        return (recent - prev) / prev * 100.0;
    }
}
