package com.gongguhan.global.security;

import com.gongguhan.domain.account.entity.User;
import com.gongguhan.domain.account.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization: Bearer {accessToken}을 파싱해 SecurityContext에 인증 정보를 채운다.
 * principal은 userId(Long)이다.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";
    private static final String ADMIN_ROLE = "ADMIN";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && jwtTokenProvider.isValidAccessToken(token)) {
            Claims claims = jwtTokenProvider.parseClaims(token);
            Long subjectId = Long.valueOf(claims.getSubject());
            String role = claims.get("role", String.class);

            if (ADMIN_ROLE.equals(role)) {
                // 관리자는 users가 아니라 admins 테이블에 있다. users 조회를 요구하면 admin
                // 토큰이 항상 인증에 실패하므로, ADMIN 역할은 별도 계정 조회 없이 인증한다
                // (/auth/admins/login이 admins를 검증해 발급한 토큰만 여기 도달한다).
                authenticateAdmin(subjectId);
            } else {
                userRepository.findById(subjectId)
                        .filter(user -> "ACTIVE".equals(user.getStatus()))
                        .ifPresent(user -> authenticate(user, role));
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticateAdmin(Long adminId) {
        var authentication = new UsernamePasswordAuthenticationToken(
                adminId, null, List.of(new SimpleGrantedAuthority("ROLE_" + ADMIN_ROLE)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void authenticate(User user, String role) {
        List<GrantedAuthority> authorities = user.isSeller()
                ? List.of(new SimpleGrantedAuthority("ROLE_" + role), new SimpleGrantedAuthority("ROLE_SELLER"))
                : List.of(new SimpleGrantedAuthority("ROLE_" + role));

        var authentication = new UsernamePasswordAuthenticationToken(user.getUserId(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader(HEADER);
        if (bearer != null && bearer.startsWith(PREFIX)) {
            return bearer.substring(PREFIX.length());
        }
        return null;
    }
}
