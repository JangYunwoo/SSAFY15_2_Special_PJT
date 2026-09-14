package com.gongguhan.domain.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.account.dto.LoginRequest;
import com.gongguhan.domain.account.dto.SignupRequest;
import com.gongguhan.domain.account.entity.User;
import com.gongguhan.domain.account.repository.UserAddressRepository;
import com.gongguhan.domain.account.repository.UserRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import com.gongguhan.global.security.JwtTokenProvider;
import com.gongguhan.domain.account.service.SsafyMemberClient.SsafyMemberResponse;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private SsafyMemberClient ssafyMemberClient;
    private AuthService authService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        ssafyMemberClient = mock(SsafyMemberClient.class);
        ObjectProvider<AuthService> self = mock(ObjectProvider.class);
        authService = new AuthService(
                userRepository,
                mock(UserAddressRepository.class),
                passwordEncoder,
                mock(JwtTokenProvider.class),
                ssafyMemberClient,
                self);
        // self.getObject()로 프록시를 거치는 대신 테스트에서는 같은 인스턴스를 돌려준다.
        when(self.getObject()).thenReturn(authService);
    }

    @Test
    void inactiveUserCannotLogin() {
        User user = mock(User.class);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(user.getPasswordHash()).thenReturn("encoded-password");
        when(user.getStatus()).thenReturn("WITHDRAWN");
        when(passwordEncoder.matches("password", "encoded-password")).thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.login(new LoginRequest("user@example.com", "password")));

        assertEquals(ErrorCode.ACCOUNT_NOT_ACTIVE, exception.getErrorCode());
    }

    @Test
    void concurrentDuplicateEmailIsReportedAsConflict() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password!234")).thenReturn("encoded-password");
        when(ssafyMemberClient.createMember("user@example.com"))
                .thenReturn(new SsafyMemberResponse("user-key", null, null));
        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(userRepository).saveAndFlush(any(User.class));
        SignupRequest request = new SignupRequest(
                "user@example.com",
                "Password!234",
                "공구왕",
                "01012345678",
                new SignupRequest.AddressRequest(
                        "홍길동", "01012345678", "06236", "서울특별시 강남구", "101호"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.signup(request));

        assertEquals(ErrorCode.EMAIL_ALREADY_EXISTS, exception.getErrorCode());
    }
}
