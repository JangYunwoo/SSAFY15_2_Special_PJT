package com.gongguhan.domain.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 판매자 전환용 휴대폰·계좌 인증 이력(SEL-01~04).
 *
 * <p>{@code updated_at}이 없는 테이블이라 {@link com.gongguhan.global.common.BaseTimeEntity}를
 * 상속하지 않고 {@code createdAt}만 직접 관리한다. 상태 기계는 REQUESTED → VERIFIED /
 * EXPIRED / FAILED 로만 전이한다.
 */
@Entity
@Table(name = "seller_verifications")
public class SellerVerification {

    public static final String TYPE_PHONE = "PHONE";
    public static final String TYPE_ACCOUNT_1WON = "ACCOUNT_1WON";

    public static final String STATUS_REQUESTED = "REQUESTED";
    public static final String STATUS_VERIFIED = "VERIFIED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "verification_id")
    private Long verificationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "verification_type", nullable = false, length = 20)
    private String verificationType;

    @Column(name = "target_value", nullable = false, length = 50)
    private String targetValue;

    @Column(name = "code_hash")
    private String codeHash;

    @Column(name = "ssafy_auth_id", length = 60)
    private String ssafyAuthId;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected SellerVerification() {
    }

    private SellerVerification(Long userId, String verificationType, String targetValue,
                               String codeHash, String ssafyAuthId, LocalDateTime expiresAt) {
        this.userId = userId;
        this.verificationType = verificationType;
        this.targetValue = targetValue;
        this.codeHash = codeHash;
        this.ssafyAuthId = ssafyAuthId;
        this.status = STATUS_REQUESTED;
        this.attemptCount = 0;
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now();
    }

    public static SellerVerification request(Long userId, String verificationType, String targetValue,
                                             String codeHash, String ssafyAuthId, LocalDateTime expiresAt) {
        return new SellerVerification(userId, verificationType, targetValue, codeHash, ssafyAuthId, expiresAt);
    }

    public boolean isExpired(LocalDateTime now) {
        return now.isAfter(expiresAt);
    }

    public boolean isVerified() {
        return STATUS_VERIFIED.equals(status);
    }

    public void markVerified(LocalDateTime now) {
        this.status = STATUS_VERIFIED;
        this.verifiedAt = now;
    }

    public void markFailed() {
        this.attemptCount++;
        this.status = STATUS_FAILED;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTargetValue() {
        return targetValue;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public String getSsafyAuthId() {
        return ssafyAuthId;
    }

    public String getStatus() {
        return status;
    }
}
