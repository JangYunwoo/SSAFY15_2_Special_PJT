package com.gongguhan.domain.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.account.dto.SignupRequest;
import com.gongguhan.domain.account.entity.User;
import com.gongguhan.domain.account.repository.UserAddressRepository;
import com.gongguhan.domain.account.repository.UserRepository;
import com.gongguhan.domain.account.service.SsafyMemberClient.SsafyMemberResponse;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import com.gongguhan.global.security.JwtTokenProvider;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceSsafyLinkTest {

    private static final Long USER_ID = 1L;
    private static final String EMAIL = "buyer@test.com";
    private static final String USER_KEY = "user-key-123";

    private UserRepository userRepository;
    private UserAddressRepository userAddressRepository;
    private SsafyMemberClient ssafyMemberClient;
    private AuthService authService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userRepository = mock(UserRepository.class);
        userAddressRepository = mock(UserAddressRepository.class);
        ssafyMemberClient = mock(SsafyMemberClient.class);
        ObjectProvider<AuthService> self = mock(ObjectProvider.class);
        authService = new AuthService(
                userRepository, userAddressRepository,
                mock(PasswordEncoder.class), mock(JwtTokenProvider.class), ssafyMemberClient, self);
        when(self.getObject()).thenReturn(authService);
    }

    private SignupRequest signupRequest() {
        return new SignupRequest(EMAIL, "password1!", "구매자", "01012345678",
                new SignupRequest.AddressRequest("홍길동", "01012345678", "06236", "서울시 강남구", "101호"));
    }

    private SsafyMemberResponse alreadyExists() {
        return new SsafyMemberResponse(null, SsafyMemberClient.RESPONSE_CODE_ALREADY_EXISTS, "이미 존재하는 ID");
    }

    private SsafyMemberResponse notFound() {
        return new SsafyMemberResponse(null, SsafyMemberClient.RESPONSE_CODE_NOT_FOUND, "존재하지 않는 ID");
    }

    private User userWithoutKey() {
        User user = User.create(EMAIL, "hash", "구매자", "01012345678");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void linksUserKeyOnCreateSuccess() {
        User user = userWithoutKey();
        when(ssafyMemberClient.createMember(EMAIL))
                .thenReturn(new SsafyMemberResponse(USER_KEY, null, null));

        authService.linkSsafyMemberIfMissing(USER_ID);

        assertEquals(USER_KEY, user.getSsafyUserKey());
        verify(userRepository).save(user);
        verify(ssafyMemberClient, never()).searchMember(any());
    }

    @Test
    void recoversUserKeyViaSearchWhenEmailAlreadyRegistered() {
        User user = userWithoutKey();
        when(ssafyMemberClient.createMember(EMAIL)).thenReturn(
                new SsafyMemberResponse(null, SsafyMemberClient.RESPONSE_CODE_ALREADY_EXISTS, "이미 존재하는 ID"));
        when(ssafyMemberClient.searchMember(EMAIL))
                .thenReturn(new SsafyMemberResponse(USER_KEY, null, null));

        authService.linkSsafyMemberIfMissing(USER_ID);

        assertEquals(USER_KEY, user.getSsafyUserKey());
        verify(userRepository).save(user);
    }

    @Test
    void skipsWhenUserKeyAlreadyLinked() {
        User user = userWithoutKey();
        user.linkSsafyUserKey(USER_KEY);

        authService.linkSsafyMemberIfMissing(USER_ID);

        verify(ssafyMemberClient, never()).createMember(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void swallowsExternalFailureWithoutBreakingCaller() {
        User user = userWithoutKey();
        when(ssafyMemberClient.createMember(EMAIL)).thenThrow(
                new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "SSAFY 장애"));

        authService.linkSsafyMemberIfMissing(USER_ID);

        assertNull(user.getSsafyUserKey());
        verify(userRepository, never()).save(any());
    }

    // --- 가입 시 SSAFY 선점 판별 (S15P21B205-63) ---

    @Test
    void signupRejectsEmailTakenByAnotherApiKey() {
        // 전역엔 존재(E4002)하지만 우리 apiKey로는 조회 불가(E4003) = 다른 apiKey 선점
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(ssafyMemberClient.createMember(EMAIL)).thenReturn(alreadyExists());
        when(ssafyMemberClient.searchMember(EMAIL)).thenReturn(notFound());

        BusinessException e = assertThrows(BusinessException.class,
                () -> authService.signup(signupRequest()));

        assertEquals(ErrorCode.SSAFY_EMAIL_UNAVAILABLE, e.getErrorCode());
        verify(userRepository, never()).saveAndFlush(any());
        verify(userAddressRepository, never()).save(any());
    }

    @Test
    void signupLinksUserKeyOnCreateSuccess() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(ssafyMemberClient.createMember(EMAIL))
                .thenReturn(new SsafyMemberResponse(USER_KEY, null, null));

        authService.signup(signupRequest());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertEquals(USER_KEY, captor.getValue().getSsafyUserKey());
        verify(userAddressRepository).save(any());
    }

    @Test
    void signupProceedsWithoutUserKeyWhenSsafyUnavailable() {
        // 금융망 장애는 가입을 막지 않는다 - userKey 없이 가입되고 로그인 때 재시도된다.
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(ssafyMemberClient.createMember(EMAIL)).thenThrow(
                new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "SSAFY 장애"));

        authService.signup(signupRequest());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertNull(captor.getValue().getSsafyUserKey());
        verify(userAddressRepository).save(any());
    }
}
