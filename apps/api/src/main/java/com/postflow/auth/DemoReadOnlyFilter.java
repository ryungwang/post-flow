package com.postflow.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * 체험(데모) 세션 읽기전용 강제 — DEMO 권한 principal이 변경 요청(GET/HEAD/OPTIONS 외)을 보내면 차단한다.
 * 공유 데모 계정(synub-sso {@code demo-user})을 아무나 변조·오염시키지 못하게 하는 안전장치.
 * JwtAuthenticationFilter가 인증을 세팅한 뒤 동작하도록 그 뒤에 등록한다(office DemoReadOnlyFilter와 동일).
 */
@Component
public class DemoReadOnlyFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final ObjectMapper objectMapper;

    public DemoReadOnlyFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isBlockedDemoWrite(request)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(), Map.of(
                    "error", "demo_read_only",
                    "message", "체험(데모) 모드에서는 저장·변경할 수 없어요. 실제 이용은 구독 후 가능해요."));
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isBlockedDemoWrite(HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod())) {
            return false;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "DEMO".equals(a.getAuthority()));
    }
}
