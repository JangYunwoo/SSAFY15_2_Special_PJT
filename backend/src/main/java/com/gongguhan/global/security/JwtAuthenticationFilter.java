package com.gongguhan.global.security;

import com.gongguhan.domain.account.entity.User;
import com.gongguhan.domain.account.repository.UserRepository;
import com.gongguhan.domain.admin.repository.AdminRepository;
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
 * principal은 역할에 따라 userId 또는 adminId(Long)다. 경로별 역할 검증으로 이름공간을 분리한다.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";
    private static final String ADMIN_ROLE = "ADMIN";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserRepository userRepository,
                                   AdminRepository adminRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
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
                // 서명 유효성은 현재 계정의 활성 상태를 보장하지 않는다.
                adminRepository.findById(subjectId)
                        .filter(admin -> admin.isActive())
                        .ifPresent(admin -> authenticateAdmin(admin.getAdminId()));
            } else if ("USER".equals(role)) {
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
