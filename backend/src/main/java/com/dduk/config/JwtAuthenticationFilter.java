package com.dduk.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends GenericFilterBean {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        try {
            if (request instanceof HttpServletRequest) {
                HttpServletRequest httpRequest = (HttpServletRequest) request;
                String header = httpRequest.getHeader("Authorization");

                // Authorization 헤더 null, empty, "Bearer " 미포함 여부를 안전하게 검증
                if (header != null && !header.trim().isEmpty() && header.startsWith("Bearer ")) {
                    try {
                        String token = jwtTokenProvider.resolveToken(httpRequest);
                        if (token != null && jwtTokenProvider.validateToken(token)) {
                            Authentication authentication = jwtTokenProvider.getAuthentication(token);
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        }
                    } catch (Exception e) {
                        SecurityContextHolder.clearContext();
                    }
                }
            }
        } catch (Exception e) {
            // 어떠한 예외 상황이 발생해도 SecurityContextHolder를 정리하고 필터 통과를 보장
            SecurityContextHolder.clearContext();
        }

        chain.doFilter(request, response);
    }
}
