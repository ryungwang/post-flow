package com.postflow.auth;

import com.postflow.user.User;
import com.postflow.user.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

/**
 * Reads a {@code Bearer} SSO access token, verifies it (RS256 via JWKS), resolves the local
 * user for its {@code sub}(external_id), and sets that user id as the authenticated principal.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder ssoJwtDecoder;
    private final UserService userService;

    public JwtAuthenticationFilter(JwtDecoder ssoJwtDecoder, UserService userService) {
        this.ssoJwtDecoder = ssoJwtDecoder;
        this.userService = userService;
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
                Long userId = userService.resolveBySso(
                        jwt.getSubject(), jwt.getClaimAsString("email"), jwt.getClaimAsString("name"));
                // 데모(체험) 계정이면 DEMO 권한 부여 → DemoReadOnlyFilter가 쓰기 요청을 차단.
                var authorities = User.DEMO_EXTERNAL_ID.equals(jwt.getSubject())
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
}
