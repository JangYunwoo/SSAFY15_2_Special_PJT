package com.gongguhan.domain.account.entity;

import com.gongguhan.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(length = 20)
    private String phone;

    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified = false;

    @Column(name = "ssafy_user_key", length = 60)
    private String ssafyUserKey;

    @Column(name = "ssafy_account_no", length = 16)
    private String ssafyAccountNo;

    @Column(name = "wallet_address", length = 42)
    private String walletAddress;

    @Column(name = "is_seller", nullable = false)
    private boolean seller = false;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    protected User() {
    }

    private User(String email, String passwordHash, String nickname, String phone) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.phone = phone;
    }

    public static User create(String email, String passwordHash, String nickname, String phone) {
        return new User(email, passwordHash, nickname, phone);
    }

    /** SSAFY 금융망 member 생성·조회로 얻은 userKey 연동 (S15P21B205-63). */
    public void linkSsafyUserKey(String ssafyUserKey) {
        this.ssafyUserKey = ssafyUserKey;
    }

    /** 휴대폰 본인인증 통과 표시(SEL-02). */
    public void markPhoneVerified() {
        this.phoneVerified = true;
    }

    /**
     * 판매자 전환 확정(SEL-05). {@code is_seller}를 켜고 정산 수취 계좌를 함께 기록한다.
     * 이후 발급되는 JWT의 isSeller 클레임이 true가 되어 {@code /sellers/**}에 접근할 수 있다.
     */
    public void promoteToSeller(String ssafyAccountNo) {
        this.seller = true;
        if (ssafyAccountNo != null && !ssafyAccountNo.isBlank()) {
            this.ssafyAccountNo = ssafyAccountNo;
        }
    }

    /** USR-02 부분 수정. null 필드는 유지한다. */
    public void updateProfile(String nickname, String phone, String ssafyAccountNo) {
        if (nickname != null) {
            this.nickname = nickname;
        }
        if (phone != null) {
            this.phone = phone;
        }
        if (ssafyAccountNo != null) {
            this.ssafyAccountNo = ssafyAccountNo;
        }
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getNickname() {
        return nickname;
    }

    public String getPhone() {
        return phone;
    }

    public String getSsafyUserKey() {
        return ssafyUserKey;
    }

    public String getSsafyAccountNo() {
        return ssafyAccountNo;
    }

    public boolean isSeller() {
        return seller;
    }

    public String getStatus() {
        return status;
    }
}
