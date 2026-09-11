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

    public String getSsafyUserKey() {
        return ssafyUserKey;
    }

    public boolean isSeller() {
        return seller;
    }

    public String getStatus() {
        return status;
    }
}
