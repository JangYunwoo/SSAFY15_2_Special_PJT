package com.gongguhan.domain.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 관리자 계정(admins). 구매자·판매자와 분리된 인증 주체다({@code users}와 별개 테이블).
 *
 * <p>{@code updated_at}이 없어 {@link com.gongguhan.global.common.BaseTimeEntity}를 상속하지
 * 않는다. JWT에는 role="ADMIN"으로 발급되어 {@code /admin/**}(ROLE_ADMIN)에 접근한다.
 */
@Entity
@Table(name = "admins")
public class Admin {

    private static final String STATUS_ACTIVE = "ACTIVE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "admin_id")
    private Long adminId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Admin() {
    }

    /** 초기 관리자 시드용 팩토리. {@code updated_at}이 없어 created_at을 직접 채운다. */
    public static Admin create(String email, String passwordHash, String name, String role) {
        Admin admin = new Admin();
        admin.email = email;
        admin.passwordHash = passwordHash;
        admin.name = name;
        admin.role = role;
        admin.status = STATUS_ACTIVE;
        admin.createdAt = LocalDateTime.now();
        return admin;
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    public Long getAdminId() {
        return adminId;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }
}
