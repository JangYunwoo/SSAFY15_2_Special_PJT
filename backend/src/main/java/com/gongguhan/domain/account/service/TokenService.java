package com.gongguhan.domain.account.service;

import com.gongguhan.domain.account.dto.ActorResponse;
import com.gongguhan.domain.account.dto.LoginResponse;
import com.gongguhan.domain.account.entity.User;
import com.gongguhan.domain.account.repository.UserRepository;
import com.gongguhan.domain.admin.repository.AdminRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import com.gongguhan.global.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 토큰 재발급·로그아웃 (AUTH-03·04).
 *
 * <p><b>재발급(AUTH-03)</b>은 리프레시 토큰을 검증하고 새 access/refresh를 발급한다.
 * 재사용을 막기 위해 <b>로테이션</b>한다 - 쓰인 리프레시 토큰은 {@link RefreshTokenDenylist}에
 * 남은 만료시간만큼 담아 다시 못 쓰게 한다. isSeller는 토큰 값이 아니라 현재 DB 상태로
 * 갱신해, 판매자 전환 후 재발급 시 권한이 반영되게 한다.
 *
 * <p><b>로그아웃(AUTH-04)</b>은 리프레시 토큰을 denylist에 올려 폐기한다(무상태 JWT라
 * 서버가 개별 무효화하려면 저장이 필요하다).
 */
@Service
@Transactional(readOnly = true)
public class TokenService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenDenylist denylist;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;

    public TokenService(JwtTokenProvider jwtTokenProvider, RefreshTokenDenylist denylist,
                        UserRepository userRepository, AdminRepository adminRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.denylist = denylist;
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
    }

    public LoginResponse refresh(String refreshToken) {
        if (!jwtTokenProvider.isValidRefreshToken(refreshToken) || denylist.isRevoked(refreshToken)) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        Claims claims = jwtTokenProvider.parseClaims(refreshToken);
        Long subjectId = Long.valueOf(claims.getSubject());
        String role = claims.get("role", String.class);

        ActorResponse actor = ADMIN_ROLE.equals(role)
                ? adminActor(subjectId)
                : userActor(subjectId);
        boolean isSeller = actor.isSeller();

        // 로테이션: 쓰인 리프레시 토큰을 남은 만료시간만큼 폐기한다.
        denylist.revoke(refreshToken, remaining(claims));

        String accessToken = jwtTokenProvider.createAccessToken(subjectId, role, isSeller);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(subjectId, role, isSeller);
        return new LoginResponse(accessToken, newRefreshToken, "Bearer",
                jwtTokenProvider.getAccessTokenExpireSeconds(), actor);
    }

    public void logout(String refreshToken) {
        // 유효하지 않은 토큰이면 폐기할 것도 없으니 조용히 넘어간다(로그아웃은 항상 성공).
        if (!jwtTokenProvider.isValidRefreshToken(refreshToken)) {
            return;
        }
        denylist.revoke(refreshToken, remaining(jwtTokenProvider.parseClaims(refreshToken)));
    }

    private ActorResponse userActor(Long userId) {
        User user = userRepository.findById(userId)
                .filter(u -> "ACTIVE".equals(u.getStatus()))
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED));
        return new ActorResponse(String.valueOf(userId), "USER", user.isSeller(), user.getNickname());
    }

    private ActorResponse adminActor(Long adminId) {
        return adminRepository.findById(adminId)
                .filter(admin -> admin.isActive())
                .map(admin -> new ActorResponse(String.valueOf(adminId), ADMIN_ROLE, false, admin.getName()))
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED));
    }

    private Duration remaining(Claims claims) {
        if (claims.getExpiration() == null) {
            return Duration.ZERO;
        }
        return Duration.between(Instant.now(), claims.getExpiration().toInstant());
    }
}
