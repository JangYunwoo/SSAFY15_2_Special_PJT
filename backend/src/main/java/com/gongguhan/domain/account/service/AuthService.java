package com.gongguhan.domain.account.service;

import com.gongguhan.domain.account.dto.ActorResponse;
import com.gongguhan.domain.account.dto.LoginRequest;
import com.gongguhan.domain.account.dto.LoginResponse;
import com.gongguhan.domain.account.dto.SignupRequest;
import com.gongguhan.domain.account.dto.SignupResponse;
import com.gongguhan.domain.account.entity.User;
import com.gongguhan.domain.account.entity.UserAddress;
import com.gongguhan.domain.account.repository.UserAddressRepository;
import com.gongguhan.domain.account.repository.UserRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import com.gongguhan.global.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private static final String DEFAULT_ROLE = "USER";

    private final UserRepository userRepository;
    private final UserAddressRepository userAddressRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(UserRepository userRepository,
                        UserAddressRepository userAddressRepository,
                        PasswordEncoder passwordEncoder,
                        JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.userAddressRepository = userAddressRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = User.create(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.nickname(),
                request.phone());
        userRepository.save(user);

        SignupRequest.AddressRequest address = request.defaultAddress();
        userAddressRepository.save(UserAddress.createDefault(
                user.getUserId(),
                address.recipientName(),
                address.recipientPhone(),
                address.zipcode(),
                address.address1(),
                address.address2()));

        return new SignupResponse(String.valueOf(user.getUserId()), user.getEmail(), user.getNickname());
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        String accessToken = jwtTokenProvider.createAccessToken(user.getUserId(), DEFAULT_ROLE, user.isSeller());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getUserId(), DEFAULT_ROLE, user.isSeller());

        ActorResponse actor = new ActorResponse(
                String.valueOf(user.getUserId()), DEFAULT_ROLE, user.isSeller(), user.getNickname());

        return new LoginResponse(
                accessToken, refreshToken, "Bearer", jwtTokenProvider.getAccessTokenExpireSeconds(), actor);
    }
}
