package com.gongguhan.domain.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * 판매 가능 여부 확인에 필요한 seller_profiles 읽기 모델.
 *
 * <p>판매자 전환 기능이 소유한 전체 프로필을 중복 구현하지 않고 식별자와 상태만
 * 매핑한다. {@link Immutable}로 변경 쿼리가 발생하지 않게 제한한다.
 */
@Entity
@Immutable
@Table(name = "seller_profiles")
public class SellerProfile {

    @Id
    @Column(name = "seller_id")
    private Long sellerId;

    @Column(nullable = false, length = 20)
    private String status;

    protected SellerProfile() {
    }
}
