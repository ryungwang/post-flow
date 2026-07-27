package com.postflow.naver;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 네이버 데이터랩 쇼핑인사이트(분야 내 키워드 트렌드) 클라이언트. 후보 키워드들의 최근 클릭 추이를 받아
 * "상승률" 기준으로 정렬해 급상승 후보를 만든다. 상승률은 키워드 자기 시계열 내 변화라 요청(배치)이
 * 달라도 비교 가능. ⚠️ 네이버 앱에 <b>데이터랩(쇼핑인사이트) API 사용 설정</b>이 켜져 있어야 동작한다
 * (검색 API와 별개).
 */
@Component
public class NaverTrendClient {

    private final NaverSearchProperties props;
    private final RestClient http;

    public NaverTrendClient(NaverSearchProperties props) {
        this.props = props;
        this.http = RestClient.builder().build();
    }

    public List<RisingKeyword> rising(String categoryCode, List<String> keywords) {
        if (!props.configured()) {
            throw new NaverException("네이버 검색 API 키가 설정되지 않았어요. (서버에 NAVER_CLIENT_ID/SECRET 필요)");
        }
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(35);

        List<Map<String, Object>> kw = keywords.stream().limit(5)
                .map(k -> Map.<String, Object>of("name", k, "param", List.of(k)))
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("startDate", start.toString());
        body.put("endDate", end.toString());
        body.put("timeUnit", "week");
        body.put("category", categoryCode);
        body.put("keyword", kw);

        try {
            JsonNode res = http.post()
                    .uri(URI.create(props.searchBaseUrlOrDefault() + "/v1/datalab/shopping/category/keywords"))
                    .header("X-Naver-Client-Id", props.clientId())
                    .header("X-Naver-Client-Secret", props.clientSecret())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve().body(JsonNode.class);

            List<RisingKeyword> out = new ArrayList<>();
            if (res != null) {
                for (JsonNode r : res.path("results")) {
                    String title = r.path("title").asText();
                    JsonNode data = r.path("data");
                    int n = data.size();
                    double first = n > 0 ? data.get(0).path("ratio").asDouble() : 0;
                    double last = n > 0 ? data.get(n - 1).path("ratio").asDouble() : 0;
                    double growth = first > 0 ? (last - first) / first * 100 : (last > 0 ? 100 : 0);
                    out.add(new RisingKeyword(title, round1(growth), round1(last)));
                }
            }
            out.sort((a, b) -> Double.compare(b.growth(), a.growth()));
            return out;
        } catch (RestClientResponseException e) {
            throw new NaverException("네이버 트렌드 조회에 실패했어요. (" + e.getStatusCode().value()
                    + ") 네이버 앱에 '데이터랩(쇼핑인사이트)' 사용 설정이 필요할 수 있어요.", e);
        } catch (RestClientException e) {
            throw new NaverException("네이버 트렌드 조회에 실패했어요.", e);
        }
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
