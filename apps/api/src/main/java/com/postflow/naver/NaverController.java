package com.postflow.naver;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 네이버 쇼핑 검색 — 제휴 콘텐츠 작성 시 실제 상품 정보를 근거로 끌어오기 위한 헬퍼(인증 필요).
 * 결과의 가격·이미지는 네이버 쇼핑 기준이라 쿠팡과 다를 수 있어 참고용으로만 쓴다.
 */
@RestController
@RequestMapping("/naver")
public class NaverController {

    private final NaverShopClient client;

    public NaverController(NaverShopClient client) {
        this.client = client;
    }

    @GetMapping("/shop/search")
    public List<NaverProduct> searchShop(@RequestParam String query,
                                         @RequestParam(defaultValue = "10") int display) {
        return client.searchShop(query.trim(), Math.min(Math.max(display, 1), 20));
    }

    @ExceptionHandler(NaverException.class)
    public ResponseEntity<Map<String, String>> handle(NaverException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", e.getMessage()));
    }
}
