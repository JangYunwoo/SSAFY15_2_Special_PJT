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
import org.springframework.beans.factory.ObjectProvider;
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
    /** persistNewUser의 @Transactional을 프록시로 태우기 위한 self 참조. */
    private final ObjectProvider<AuthService> self;

    public AuthService(UserRepository userRepository,
                        UserAddressRepository userAddressRepository,
                        PasswordEncoder passwordEncoder,
                        JwtTokenProvider jwtTokenProvider,
                        SsafyMemberClient ssafyMemberClient,
                        ObjectProvider<AuthService> self) {
        this.userRepository = userRepository;
        this.userAddressRepository = userAddressRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.ssafyMemberClient = ssafyMemberClient;
        this.self = self;
    }

    /**
     * 회원가입. SSAFY 금융망 연동을 <b>가입 시점에 동기로 판별</b>한다(S15P21B205-63).
     *
     * <p>SSAFY 교육용 금융망은 이메일이 전역 유니크라, 다른 apiKey가 이미 선점한
     * 이메일은 우리 apiKey로는 영원히 연동할 수 없다. 그런 계정을 조용히 만들어 두면
     * 결제 때 {@code SSAFY_MEMBER_NOT_LINKED}로 막히고 재시도도 계속 실패해 사용자가
     * 원인을 알 수 없다. 그래서 결정적 충돌은 가입 자체를 거절한다.
     *
     * <p>단, SSAFY <b>장애</b>(일시적)는 가입을 막지 않는다 - userKey 없이 가입시키고
     * 다음 로그인에서 {@link #linkSsafyMemberIfMissing}로 백필한다(기존 방침 유지).
     *
     * <p>외부 호출은 트랜잭션 밖에서 먼저 수행하고, DB 쓰기만 별도 트랜잭션
     * ({@link #persistNewUser})으로 원자적으로 처리한다. 클래스 기본 readOnly 트랜잭션이
     * 걸리면 persistNewUser가 그것을 이어받아 쓰기가 막히므로 {@code NOT_SUPPORTED}로
     * 트랜잭션 없이 실행한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        SsafyLinkResult link = resolveSsafyMember(request.email());
        if (link.outcome() == SsafyLinkOutcome.COLLISION) {
            throw new BusinessException(ErrorCode.SSAFY_EMAIL_UNAVAILABLE);
        }

        // LINKED면 userKey를 함께 저장, UNAVAILABLE(장애)이면 null로 두고 로그인 때 재시도.
        return self.getObject().persistNewUser(request, link.userKey());
    }

    @Transactional
    public SignupResponse persistNewUser(SignupRequest request, String ssafyUserKey) {
        User user = User.create(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.nickname(),
                request.phone());
        if (ssafyUserKey != null && !ssafyUserKey.isBlank()) {
            user.linkSsafyUserKey(ssafyUserKey);
        }
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

    /**
     * 이메일 사용 가능 여부 조회(AUTH-05). 회원가입 전 중복확인 버튼용이다.
     *
     * <p>우리 서비스 DB의 유니크 제약({@code existsByEmail})만 확인하는 사이드이펙트 없는
     * 조회다. SSAFY 금융망 연동은 {@code createMember}가 실제 회원을 생성하는 쓰기라
     * 확인 단계에서 호출하지 않으며, 금융망 이메일 선점 충돌은 기존대로 가입 시점에만
     * {@code SSAFY_EMAIL_UNAVAILABLE}로 판별된다.
     *
     * @return 아직 가입되지 않았으면 {@code true}
     */
    public boolean isEmailAvailable(String email) {
        return !userRepository.existsByEmail(email);
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

        SsafyLinkResult link = resolveSsafyMember(user.getEmail());
        switch (link.outcome()) {
            case LINKED -> {
                user.linkSsafyUserKey(link.userKey());
                userRepository.save(user);
                log.info("SSAFY member 연동 완료 userId={}", userId);
            }
            // 선점 충돌은 재시도해도 안 되지만, 가입은 이미 된 계정이므로 로그만 남긴다
            // (가입 경로에서는 애초에 거절돼 이 상태로 들어오지 않는다).
            case COLLISION -> log.warn("SSAFY member 이메일 선점 충돌 userId={} - 연동 불가", userId);
            case UNAVAILABLE -> log.warn("SSAFY member 연동 실패 userId={} - 다음 로그인에서 재시도된다", userId);
        }
    }

    /**
     * 이메일에 대응하는 SSAFY member userKey를 확보한다(get-or-create).
     *
     * <ul>
     *   <li>{@code LINKED}    - 생성 성공했거나(신규), 우리 apiKey가 이미 가진 회원을 조회로 회수</li>
     *   <li>{@code COLLISION} - 다른 apiKey가 선점(create=E4002 인데 우리 apiKey 조회=E4003).
     *       재시도해도 절대 회수 불가한 결정적 실패다.</li>
     *   <li>{@code UNAVAILABLE} - 금융망 장애 등 일시적 실패. 나중에 재시도하면 될 수 있다.</li>
     * </ul>
     */
    SsafyLinkResult resolveSsafyMember(String email) {
        try {
            SsafyMemberResponse response = ssafyMemberClient.createMember(email);
            if (response.hasUserKey()) {
                return new SsafyLinkResult(SsafyLinkOutcome.LINKED, response.userKey());
            }
            if (response.isAlreadyExists()) {
                // 전역엔 존재(E4002). 우리 apiKey 소유면 조회로 userKey를 회수할 수 있다.
                SsafyMemberResponse found = ssafyMemberClient.searchMember(email);
                if (found.hasUserKey()) {
                    return new SsafyLinkResult(SsafyLinkOutcome.LINKED, found.userKey());
                }
                if (found.isNotFound()) {
                    // 전역엔 있는데 우리 apiKey로는 조회 불가 = 다른 apiKey가 선점.
                    return new SsafyLinkResult(SsafyLinkOutcome.COLLISION, null);
                }
            }
            log.warn("SSAFY member 연동 실패 email={} responseCode={} message={}",
                    email, response.responseCode(), response.responseMessage());
            return new SsafyLinkResult(SsafyLinkOutcome.UNAVAILABLE, null);
        } catch (BusinessException e) {
            log.warn("SSAFY member 연동 중 금융망 장애 email={}", email, e);
            return new SsafyLinkResult(SsafyLinkOutcome.UNAVAILABLE, null);
        }
    }

    enum SsafyLinkOutcome { LINKED, COLLISION, UNAVAILABLE }

    record SsafyLinkResult(SsafyLinkOutcome outcome, String userKey) {
    }
}
