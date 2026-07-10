package com.postflow.common.config;

import com.postflow.auth.DemoReadOnlyFilter;
import com.postflow.auth.JwtAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final DemoReadOnlyFilter demoReadOnlyFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          DemoReadOnlyFilter demoReadOnlyFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.demoReadOnlyFilter = demoReadOnlyFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        AuthenticationEntryPoint entryPoint = new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {})
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // error dispatch must be open, or any 4xx/404 re-dispatches to /error
                        // and gets masked as 401
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(EndpointRequest.to(HealthEndpoint.class)).permitAll()
                        .requestMatchers("/ping",
                                "/threads/callback", "/threads/deauthorize",
                                "/threads/data-deletion", "/threads/data-deletion/status",
                                "/linkedin/callback",
                                "/facebook/callback",
                                "/r/**", "/public/**",
                                "/webhooks/**", "/files/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // 인증 세팅 뒤 실행 — 데모 세션의 쓰기 요청 차단(read-only).
                .addFilterAfter(demoReadOnlyFilter, JwtAuthenticationFilter.class);
        return http.build();
    }
}
