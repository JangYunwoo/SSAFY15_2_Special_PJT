package com.gongguhan.domain.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceSsafyLinkTest {

    private static final Long USER_ID = 1L;
    private static final String EMAIL = "buyer@test.com";
    private static final String USER_KEY = "user-key-123";

    private UserRepository userRepository;
    private SsafyMemberClient ssafyMemberClient;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        ssafyMemberClient = mock(SsafyMemberClient.class);
        authService = new AuthService(
                userRepository, mock(UserAddressRepository.class),
                mock(PasswordEncoder.class), mock(JwtTokenProvider.class), ssafyMemberClient);
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
}
