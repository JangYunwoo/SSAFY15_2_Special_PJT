package com.gongguhan.domain.account.service;

import com.gongguhan.domain.account.dto.ActorResponse;
import com.gongguhan.domain.account.dto.LoginResponse;
import com.gongguhan.domain.account.dto.SellerVerificationDtos.AccountConfirmRequest;
import com.gongguhan.domain.account.dto.SellerVerificationDtos.AccountRequestResponse;
import com.gongguhan.domain.account.dto.SellerVerificationDtos.PhoneConfirmRequest;
import com.gongguhan.domain.account.dto.SellerVerificationDtos.PhoneRequest;
import com.gongguhan.domain.account.dto.SellerVerificationDtos.PhoneRequestResponse;
import com.gongguhan.domain.account.dto.SellerVerificationDtos.SellerProfileCreateRequest;
import com.gongguhan.domain.account.entity.SellerProfile;
import com.gongguhan.domain.account.entity.SellerVerification;
import com.gongguhan.domain.account.entity.User;
import com.gongguhan.domain.account.repository.SellerProfileRepository;
import com.gongguhan.domain.account.repository.SellerVerificationRepository;
import com.gongguhan.domain.account.repository.UserRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import com.gongguhan.global.security.JwtTokenProvider;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자 전환(SEL-01~05).
 *
 * <p><b>하이브리드 인증</b>: 휴대폰(SEL-01·02)은 SMS 사업자가 없어 목업(코드 생성·검증만)이고,
 * 정산계좌 1원 인증(SEL-03·04)은 실제 SSAFY 금융망({@link SsafyAccountClient})을 호출한다.
 * 전환 확정(SEL-05)은 두 인증이 모두 VERIFIED여야 하며, {@code is_seller=true}로 바꾸고
 * isSeller 클레임이 반영된 새 토큰을 발급한다(기존 토큰은 아직 구매자라 판매자센터 접근 불가).
 *
 * <p>외부 SSAFY 호출은 DB 트랜잭션 밖에서 수행하고(계좌 요청·확인 메서드는 @Transactional을
 * 걸지 않는다), 순수 DB 작업만 트랜잭션으로 묶는다(원자성 규칙 - 다른 서비스와 동일).
 */
@Service
public class SellerOnboardingService {

    private static final Logger log = LoggerFactory.getLogger(SellerOnboardingService.class);
    private static final String DEFAULT_ROLE = "USER";
    private static final Duration PHONE_TTL = Duration.ofMinutes(5);
    private static final Duration ACCOUNT_TTL = Duration.ofMinutes(10);

    private final UserRepository userRepository;
    private final SellerProfileRepository sellerProfileRepository;
    private final SellerVerificationRepository verificationRepository;
    private final SsafyAccountClient ssafyAccountClient;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public SellerOnboardingService(UserRepository userRepository,
                                   SellerProfileRepository sellerProfileRepository,
                                   SellerVerificationRepository verificationRepository,
                                   SsafyAccountClient ssafyAccountClient,
                                   PasswordEncoder passwordEncoder,
                                   JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.sellerProfileRepository = sellerProfileRepository;
        this.verificationRepository = verificationRepository;
        this.ssafyAccountClient = ssafyAccountClient;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /** SEL-01 휴대폰 인증 요청(목업). 6자리 코드를 만들어 저장하고 데모용으로 함께 돌려준다. */
    @Transactional
    public PhoneRequestResponse requestPhone(Long userId, PhoneRequest request) {
        ensureNotSeller(userId);
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        verificationRepository.save(SellerVerification.request(
                userId, SellerVerification.TYPE_PHONE, request.phone(),
                passwordEncoder.encode(code), null, LocalDateTime.now().plus(PHONE_TTL)));
        return new PhoneRequestResponse(code, "인증코드를 발송했습니다(데모: 아래 코드를 그대로 입력하세요).");
    }

    /** SEL-02 휴대폰 인증 확인. */
    @Transactional
    public void confirmPhone(Long userId, PhoneConfirmRequest request) {
        SellerVerification verification = latest(userId, SellerVerification.TYPE_PHONE);
        LocalDateTime now = LocalDateTime.now();
        if (verification.isExpired(now)
                || verification.getCodeHash() == null
                || !passwordEncoder.matches(request.code(), verification.getCodeHash())) {
            verification.markFailed();
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_MISMATCH);
        }
        verification.markVerified(now);
        userRepository.findById(userId).ifPresent(User::markPhoneVerified);
    }

    /**
     * SEL-03 정산계좌 1원 인증 요청(실제 SSAFY). 계좌가 없으면 개설하고, 1원 송금 후
     * 적요에서 인증코드를 자동으로 읽어 돌려준다. 외부 호출이라 트랜잭션 밖에서 실행한다.
     */
    public AccountRequestResponse requestAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "사용자를 찾을 수 없습니다."));
        ensureNotSeller(userId);
        if (user.getSsafyUserKey() == null || user.getSsafyUserKey().isBlank()) {
            throw new BusinessException(ErrorCode.SSAFY_MEMBER_NOT_LINKED);
        }

        String userKey = user.getSsafyUserKey();
        String accountNo = (user.getSsafyAccountNo() != null && !user.getSsafyAccountNo().isBlank())
                ? user.getSsafyAccountNo()
                : ssafyAccountClient.createAccount(userKey);
        String authCode = ssafyAccountClient.sendOneWonAuth(userKey, accountNo);

        persistAccountRequest(userId, accountNo);
        return new AccountRequestResponse(accountNo, authCode,
                "계좌로 1원을 보냈습니다. 입금 내역의 인증코드를 입력하세요(데모: 자동 조회됨).");
    }

    /** SEL-04 정산계좌 1원 인증 확인(실제 SSAFY checkAuthCode). 외부 호출이라 트랜잭션 밖. */
    public void confirmAccount(Long userId, AccountConfirmRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "사용자를 찾을 수 없습니다."));
        if (user.getSsafyUserKey() == null || user.getSsafyUserKey().isBlank()) {
            throw new BusinessException(ErrorCode.SSAFY_MEMBER_NOT_LINKED);
        }
        boolean ok = ssafyAccountClient.verifyOneWonAuth(
                user.getSsafyUserKey(), request.accountNo(), request.authCode());
        markAccountResult(userId, ok);
        if (!ok) {
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_MISMATCH);
        }
    }

    /** SEL-05 판매자 전환 확정. 두 인증이 모두 VERIFIED여야 하며 새 토큰(isSeller=true)을 발급한다. */
    @Transactional
    public LoginResponse createSellerProfile(Long userId, SellerProfileCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "사용자를 찾을 수 없습니다."));
        if (user.isSeller() || sellerProfileRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.ALREADY_SELLER);
        }

        SellerVerification phone = latest(userId, SellerVerification.TYPE_PHONE);
        SellerVerification account = latest(userId, SellerVerification.TYPE_ACCOUNT_1WON);
        if (!phone.isVerified() || !account.isVerified()) {
            throw new BusinessException(ErrorCode.SELLER_VERIFICATION_REQUIRED);
        }

        String accountNo = account.getTargetValue();
        SellerProfileCreateRequest.ReturnAddress addr = request.returnAddress();
        sellerProfileRepository.save(SellerProfile.create(
                userId, request.businessName(), request.businessPhone(),
                accountNo, request.accountHolder(),
                addr.zipcode(), addr.address1(), addr.address2()));
        user.promoteToSeller(accountNo);

        String accessToken = jwtTokenProvider.createAccessToken(userId, DEFAULT_ROLE, true);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId, DEFAULT_ROLE, true);
        ActorResponse actor = new ActorResponse(String.valueOf(userId), DEFAULT_ROLE, true, user.getNickname());
        log.info("판매자 전환 완료 userId={}", userId);
        return new LoginResponse(accessToken, refreshToken, "Bearer",
                jwtTokenProvider.getAccessTokenExpireSeconds(), actor);
    }

    // 아래 두 메서드는 외부 호출 뒤 같은 빈에서 호출되므로 @Transactional 프록시가 걸리지
    // 않는다(self-invocation). 트랜잭션에 기대지 않고 명시적으로 save 해 flush를 보장한다.
    private void persistAccountRequest(Long userId, String accountNo) {
        verificationRepository.save(SellerVerification.request(
                userId, SellerVerification.TYPE_ACCOUNT_1WON, accountNo,
                null, null, LocalDateTime.now().plus(ACCOUNT_TTL)));
        userRepository.findById(userId).ifPresent(user -> {
            user.updateProfile(null, null, accountNo);
            userRepository.save(user);
        });
    }

    private void markAccountResult(Long userId, boolean ok) {
        SellerVerification verification = latest(userId, SellerVerification.TYPE_ACCOUNT_1WON);
        if (ok) {
            verification.markVerified(LocalDateTime.now());
        } else {
            verification.markFailed();
        }
        verificationRepository.save(verification);
    }

    private SellerVerification latest(Long userId, String type) {
        return verificationRepository.findTopByUserIdAndVerificationTypeOrderByCreatedAtDesc(userId, type)
                .orElseThrow(() -> new BusinessException(ErrorCode.SELLER_VERIFICATION_REQUIRED));
    }

    private void ensureNotSeller(Long userId) {
        if (sellerProfileRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.ALREADY_SELLER);
        }
    }
}
