package com.postflow.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.postflow.user.User;
import com.postflow.user.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads a {@code Bearer} SSO access token, verifies it (RS256 via JWKS), resolves the local
 * user for its {@code sub}(external_id), and sets that user id as the authenticated principal.
 *
 * <p><b>비공개 접근 허용목록</b>: 오픈 전이라 {@code auth.allowed-external-ids}에 지정된 external_id만
 * 로그인 가능(데모 계정 {@code demo-user}은 항상 허용 — 읽기전용 체험). 목록이 비면(prod 공개 시)
 * 전체 허용. 허용 안 된 SSO 계정은 토큰이 유효해도 403.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder ssoJwtDecoder;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    /** 허용된 external_id 집합(비면 전체 허용). demo-user는 이와 별개로 항상 허용. */
    private final Set<String> allowedExternalIds;

    public JwtAuthenticationFilter(JwtDecoder ssoJwtDecoder, UserService userService,
                                   ObjectMapper objectMapper,
                                   @Value("${auth.allowed-external-ids:}") String allowedCsv) {
        this.ssoJwtDecoder = ssoJwtDecoder;
        this.userService = userService;
        this.objectMapper = objectMapper;
        this.allowedExternalIds = Arrays.stream(allowedCsv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Jwt jwt = ssoJwtDecoder.decode(token);
                String externalId = jwt.getSubject();
                if (!isAllowed(externalId)) {
                    // 토큰은 유효하나 접근 허가 계정이 아님(비공개 베타).
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    objectMapper.writeValue(response.getWriter(), Map.of(
                            "error", "not_allowed",
                            "message", "아직 비공개 베타예요. 허가된 계정만 이용할 수 있어요."));
                    return;
                }
                Long userId = userService.resolveBySso(
                        externalId, jwt.getClaimAsString("email"), jwt.getClaimAsString("name"));
                // 데모(체험) 계정이면 DEMO 권한 부여 → DemoReadOnlyFilter가 쓰기 요청을 차단.
                var authorities = User.DEMO_EXTERNAL_ID.equals(externalId)
                        ? java.util.List.of(new SimpleGrantedAuthority("DEMO"))
                        : AuthorityUtils.NO_AUTHORITIES;
                var auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException ignored) {
                // invalid/expired token → leave unauthenticated; protected endpoints return 401
            }
        }
        filterChain.doFilter(request, response);
    }

    /** 허용목록이 비면 전체 허용, 아니면 목록에 있거나 데모 계정일 때만 허용. */
    private boolean isAllowed(String externalId) {
        if (allowedExternalIds.isEmpty()) {
            return true;
        }
        return allowedExternalIds.contains(externalId) || User.DEMO_EXTERNAL_ID.equals(externalId);
    }
}
