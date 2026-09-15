package com.gongguhan.domain.admin.service;

import com.gongguhan.domain.account.dto.ActorResponse;
import com.gongguhan.domain.account.dto.LoginResponse;
import com.gongguhan.domain.admin.dto.AdminLoginRequest;
import com.gongguhan.domain.admin.entity.Admin;
import com.gongguhan.domain.admin.repository.AdminRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import com.gongguhan.global.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 로그인(AUTH-05). {@code users}와 분리된 {@code admins}를 검증하고 role="ADMIN"
 * JWT를 발급한다. 이 토큰이 {@link com.gongguhan.global.security.JwtAuthenticationFilter}에서
 * ROLE_ADMIN 권한으로 인증되어 {@code /admin/**}에 접근한다.
 */
@Service
@Transactional(readOnly = true)
public class AdminAuthService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AdminAuthService(AdminRepository adminRepository, PasswordEncoder passwordEncoder,
                            JwtTokenProvider jwtTokenProvider) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public LoginResponse login(AdminLoginRequest request) {
        Admin admin = adminRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        if (!passwordEncoder.matches(request.password(), admin.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!admin.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        // isSeller=false. principal은 adminId이며 users가 아니라 admins를 가리킨다.
        String accessToken = jwtTokenProvider.createAccessToken(admin.getAdminId(), ADMIN_ROLE, false);
        String refreshToken = jwtTokenProvider.createRefreshToken(admin.getAdminId(), ADMIN_ROLE, false);
        ActorResponse actor = new ActorResponse(
                String.valueOf(admin.getAdminId()), ADMIN_ROLE, false, admin.getName());
        return new LoginResponse(accessToken, refreshToken, "Bearer",
                jwtTokenProvider.getAccessTokenExpireSeconds(), actor);
    }
}
