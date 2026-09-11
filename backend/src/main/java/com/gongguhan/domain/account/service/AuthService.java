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
import com.gongguhan.domain.account.service.SsafyMemberClient.SsafyMemberResponse;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import com.gongguhan.global.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String DEFAULT_ROLE = "USER";

    private final UserRepository userRepository;
    private final UserAddressRepository userAddressRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final SsafyMemberClient ssafyMemberClient;

    public AuthService(UserRepository userRepository,
                        UserAddressRepository userAddressRepository,
                        PasswordEncoder passwordEncoder,
                        JwtTokenProvider jwtTokenProvider,
                        SsafyMemberClient ssafyMemberClient) {
        this.userRepository = userRepository;
        this.userAddressRepository = userAddressRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.ssafyMemberClient = ssafyMemberClient;
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
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

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

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        String accessToken = jwtTokenProvider.createAccessToken(user.getUserId(), DEFAULT_ROLE, user.isSeller());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getUserId(), DEFAULT_ROLE, user.isSeller());

        ActorResponse actor = new ActorResponse(
                String.valueOf(user.getUserId()), DEFAULT_ROLE, user.isSeller(), user.getNickname());

        return new LoginResponse(
                accessToken, refreshToken, "Bearer", jwtTokenProvider.getAccessTokenExpireSeconds(), actor);
    }

    /**
     * SSAFY 금융망 member 연동 (S15P21B205-63). userKey가 없으면 생성을 시도하고,
     * 이미 존재하는 이메일(E4002)이면 조회로 회수한다.
     *
     * <p><b>가입·로그인을 실패시키지 않는다.</b> SSAFY 장애가 계정 생성까지 막으면 안
     * 되므로(후속 등록 방침), 실패는 경고 로그만 남긴다 - userKey가 비어 있으면 카드
     * 결제 시점에 {@code SSAFY_MEMBER_NOT_LINKED}로 드러나고, 다음 로그인에서 여기로
     * 다시 들어와 재시도된다.
     *
     * <p>외부 호출을 트랜잭션 밖에서 하기 위해 {@code NOT_SUPPORTED}로 선언한다
     * (원자성 규칙 - {@code PaymentService.prepare}와 같은 패턴). 저장은
     * {@code userRepository.save}의 자체 트랜잭션으로 짧게 처리된다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void linkSsafyMemberIfMissing(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || (user.getSsafyUserKey() != null && !user.getSsafyUserKey().isBlank())) {
            return;
        }

        try {
            SsafyMemberResponse response = ssafyMemberClient.createMember(user.getEmail());
            if (!response.hasUserKey() && response.isAlreadyExists()) {
                response = ssafyMemberClient.searchMember(user.getEmail());
            }
            if (response.hasUserKey()) {
                user.linkSsafyUserKey(response.userKey());
                userRepository.save(user);
                log.info("SSAFY member 연동 완료 userId={}", userId);
            } else {
                log.warn("SSAFY member 연동 실패 userId={} responseCode={} message={}",
                        userId, response.responseCode(), response.responseMessage());
            }
        } catch (BusinessException e) {
            log.warn("SSAFY member 연동 중 금융망 장애 userId={} - 다음 로그인에서 재시도된다", userId, e);
        }
    }
}
