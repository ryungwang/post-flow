package com.postflow.threads;

import com.fasterxml.jackson.databind.JsonNode;
import com.postflow.threads.dto.ThreadsAccountDto;
import com.postflow.threads.dto.ThreadsStatusResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/threads")
public class ThreadsController {

    private static final Logger log = LoggerFactory.getLogger(ThreadsController.class);

    private final ThreadsOAuthService oAuthService;
    private final SocialAccountService socialAccountService;
    private final ThreadsProperties props;

    public ThreadsController(ThreadsOAuthService oAuthService,
                             SocialAccountService socialAccountService,
                             ThreadsProperties props) {
        this.oAuthService = oAuthService;
        this.socialAccountService = socialAccountService;
        this.props = props;
    }

    /** Returns the Threads authorize URL for the frontend to redirect the browser to. */
    @GetMapping("/connect")
    public Map<String, String> connect(@AuthenticationPrincipal Long userId) {
        // single-account plans replace their connection in connectFromCode; UI gates "add account".
        return Map.of("authorizeUrl", oAuthService.buildAuthorizeUrl(userId));
    }

    /** All connected Threads accounts for the user. */
    @GetMapping("/accounts")
    public List<ThreadsAccountDto> accounts(@AuthenticationPrincipal Long userId) {
        return socialAccountService.list(userId);
    }

    /** 연결된 계정 게시물 한 페이지(+PostFlow 발행 여부, 다음 커서). 무한 스크롤용. */
    @GetMapping("/posts")
    public com.postflow.threads.dto.AccountPostsPage accountPosts(
            @AuthenticationPrincipal Long userId,
            @RequestParam(name = "accountId", required = false) Long accountId,
            @RequestParam(name = "limit", defaultValue = "10") int limit,
            @RequestParam(name = "after", required = false) String after) {
        return socialAccountService.accountPosts(userId, accountId, Math.min(Math.max(limit, 1), 100), after);
    }

    /** 계정 인사이트: 팔로워 수 + 팔로워 인구통계(연령/성별/국가/도시). */
    @GetMapping("/insights")
    public com.postflow.threads.dto.ThreadsInsightsDto accountInsights(
            @AuthenticationPrincipal Long userId,
            @RequestParam(name = "accountId", required = false) Long accountId) {
        return socialAccountService.accountInsights(userId, accountId);
    }

    /** 공개 프로필(경쟁사/인플루언서) 조회. null이면 권한 미보유/미존재/팔로워 100 미만. */
    @GetMapping("/profile-lookup")
    public com.postflow.threads.api.ThreadsProfileLookup profileLookup(
            @AuthenticationPrincipal Long userId, @RequestParam(name = "username") String username) {
        return socialAccountService.lookupProfile(userId, username);
    }

    /** 나를 멘션한 게시물. available=false면 manage_mentions 권한 미보유. */
    @GetMapping("/mentions")
    public com.postflow.threads.dto.TrendResult mentions(
            @AuthenticationPrincipal Long userId,
            @RequestParam(name = "accountId", required = false) Long accountId,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return socialAccountService.mentions(userId, accountId, Math.min(Math.max(limit, 1), 50));
    }

    /** Threads 게시물 삭제(threads_delete). */
    @DeleteMapping("/posts/{mediaId}")
    public java.util.Map<String, Boolean> deletePost(
            @AuthenticationPrincipal Long userId, @PathVariable String mediaId) {
        return java.util.Map.of("deleted", socialAccountService.deleteMedia(userId, mediaId));
    }

    /** 키워드로 지금 뜨는 공개 게시물 검색(트렌드). available=false면 keyword_search 권한 미보유. */
    @GetMapping("/trends")
    public com.postflow.threads.dto.TrendResult trends(
            @AuthenticationPrincipal Long userId,
            @RequestParam(name = "q") String keyword,
            @RequestParam(name = "type", defaultValue = "TOP") String type,
            @RequestParam(name = "limit", defaultValue = "15") int limit) {
        return socialAccountService.searchTrends(userId, keyword, type, Math.min(Math.max(limit, 1), 30));
    }

    /** 특정 게시물의 댓글(전체 대화). available=false면 앱 검수(threads_manage_replies) 미승인. */
    @GetMapping("/posts/{mediaId}/replies")
    public com.postflow.threads.dto.RepliesResult replies(
            @AuthenticationPrincipal Long userId, @PathVariable String mediaId) {
        return socialAccountService.repliesFor(userId, mediaId);
    }

    @PostMapping("/accounts/{id}/default")
    public void setDefault(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        socialAccountService.setDefaultAccount(userId, id);
    }

    @DeleteMapping("/accounts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        socialAccountService.disconnect(userId, id);
    }

    /** OAuth redirect target (public). Exchanges the code, then bounces to the frontend. */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam String code, @RequestParam String state) {
        String redirect = oAuthService.handleCallback(code, state);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirect))
                .build();
    }

    @GetMapping("/status")
    public ThreadsStatusResponse status(@AuthenticationPrincipal Long userId) {
        return socialAccountService.status(userId);
    }

    /**
     * Meta deauthorize callback (public) — 사용자가 앱 연결을 해제하면 호출된다.
     * {@code signed_request}를 앱시크릿으로 검증하고, 해당 Threads 계정 연결을 서버에서 폐기한다.
     */
    @RequestMapping(value = "/deauthorize", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Void> deauthorize(@RequestParam(name = "signed_request", required = false) String signedRequest) {
        JsonNode payload = MetaSignedRequest.verify(signedRequest, props.appSecret());
        if (payload == null) {
            return ResponseEntity.ok().build(); // 검증 불가/핑 요청 — 그래도 2xx로 응답(재시도 폭주 방지)
        }
        String threadsUserId = payload.path("user_id").asText(null);
        if (threadsUserId != null) {
            int removed = socialAccountService.disconnectByThreadsUserId(threadsUserId);
            log.info("Threads deauthorize: user {} → {} account(s) removed", threadsUserId, removed);
        }
        return ResponseEntity.ok().build();
    }

    /**
     * Meta data-deletion callback (public) — 사용자의 데이터 삭제 요청. {@code signed_request} 검증 후
     * 해당 Threads 계정 연결·토큰을 삭제하고, 상태 확인 URL + 확인 코드를 반환한다(Meta 스펙 필수).
     */
    @RequestMapping(value = "/data-deletion", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Map<String, String>> dataDeletion(
            @RequestParam(name = "signed_request", required = false) String signedRequest,
            HttpServletRequest request) {
        String code = "del_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        JsonNode payload = MetaSignedRequest.verify(signedRequest, props.appSecret());
        if (payload != null) {
            String threadsUserId = payload.path("user_id").asText(null);
            if (threadsUserId != null) {
                int removed = socialAccountService.disconnectByThreadsUserId(threadsUserId);
                log.info("Threads data-deletion: user {} → {} account(s) deleted (code {})",
                        threadsUserId, removed, code);
            }
        }
        // 상태 확인 페이지 URL — 이 콜백을 받은 실제 호스트(=API 호스트)에서 파생(env 오설정에 견고).
        String base = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        String statusUrl = base + "/threads/data-deletion/status?code=" + code;
        return ResponseEntity.ok(Map.of("url", statusUrl, "confirmation_code", code));
    }

    /** 데이터 삭제 요청 상태 페이지(public) — Meta가 요구하는 사람이 읽는 처리 상태. */
    @GetMapping(value = "/data-deletion/status", produces = "text/html; charset=UTF-8")
    public String dataDeletionStatus(@RequestParam(required = false) String code) {
        String safeCode = code == null ? "-" : code.replaceAll("[^a-zA-Z0-9_]", "");
        return """
                <!doctype html><html lang="ko"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>PostFlow — 데이터 삭제 요청</title>
                <style>body{font-family:-apple-system,system-ui,sans-serif;max-width:560px;margin:10vh auto;padding:0 20px;color:#1a1a2e;line-height:1.6}h1{font-size:20px}code{background:#f2f2f7;padding:2px 8px;border-radius:6px}</style>
                </head><body>
                <h1>데이터 삭제 요청이 처리되었습니다</h1>
                <p>PostFlow는 요청을 접수하는 즉시 해당 Threads 계정 연결과 저장된 액세스 토큰을 삭제했습니다.
                추가로 남은 개인 데이터가 있다면 삭제 처리됩니다.</p>
                <p>확인 코드: <code>%s</code></p>
                <p>문의: <a href="mailto:deerkrg@gmail.com">deerkrg@gmail.com</a></p>
                </body></html>""".formatted(safeCode);
    }
}
