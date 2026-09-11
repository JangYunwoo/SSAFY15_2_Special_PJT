package com.gongguhan.domain.account.entity;

import com.gongguhan.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;

/**
 * 판매자 전환 프로필. {@code users.user_id}를 PK로 공유한다.
 * S15P21B205-63 범위는 정산 계좌·반품 주소 조회/수정(SEL-06·07)이라 그에 필요한
 * 필드만 매핑하고, 판매자 전환(SEL-05)·인증(SEL-01~04) 생성 경로는 다루지 않는다.
 */
@Entity
@Table(name = "seller_profiles")
public class SellerProfile extends BaseTimeEntity {

    @Id
    @Column(name = "seller_id")
    private Long sellerId;

    // 기본값 '상점': 기존 테스트들이 business_name 없이 seller_profiles를 MERGE하며
    // 기대하던 값(GroupBuyQueryIntegrationTest의 ALTER ... DEFAULT '상점'과 동일).
    @ColumnDefault("'상점'")
    @Column(name = "business_name", nullable = false, length = 100)
    private String businessName;

    @Column(name = "business_phone", length = 20)
    private String businessPhone;

    @Column(name = "ssafy_account_no", length = 16)
    private String ssafyAccountNo;

    @Column(name = "account_holder", length = 50)
    private String accountHolder;

    // @ColumnDefault 3종: H2(ddl-auto) 테스트 스키마에도 운영(V1)과 같은 기본값이 생겨야
    // 이 컬럼들을 모르는 기존 원시 MERGE/INSERT 테스트가 깨지지 않는다(Participation과 동일 사유).
    @ColumnDefault("false")
    @Column(name = "account_verified", nullable = false)
    private boolean accountVerified = false;

    @Column(name = "return_zipcode", length = 5)
    private String returnZipcode;

    @Column(name = "return_address1")
    private String returnAddress1;

    @Column(name = "return_address2")
    private String returnAddress2;

    @ColumnDefault("0")
    @Column(name = "penalty_count", nullable = false)
    private int penaltyCount;

    @Column(nullable = false, length = 20)
    private String status;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "verified_at", nullable = false)
    private LocalDateTime verifiedAt;

    protected SellerProfile() {
    }

    /** SEL-07 부분 수정. null 필드는 유지된다. */
    public void update(String businessPhone, String ssafyAccountNo, String accountHolder,
                        String returnZipcode, String returnAddress1, String returnAddress2) {
        if (businessPhone != null) {
            this.businessPhone = businessPhone;
        }
        if (ssafyAccountNo != null) {
            this.ssafyAccountNo = ssafyAccountNo;
            // 계좌를 바꾸면 1원 인증을 다시 받아야 한다 - 검증 플래그를 되돌린다.
            this.accountVerified = false;
        }
        if (accountHolder != null) {
            this.accountHolder = accountHolder;
        }
        if (returnZipcode != null) {
            this.returnZipcode = returnZipcode;
        }
        if (returnAddress1 != null) {
            this.returnAddress1 = returnAddress1;
        }
        if (returnAddress2 != null) {
            this.returnAddress2 = returnAddress2;
        }
    }

    public Long getSellerId() {
        return sellerId;
    }

    public String getBusinessName() {
        return businessName;
    }

    public String getBusinessPhone() {
        return businessPhone;
    }

    public String getSsafyAccountNo() {
        return ssafyAccountNo;
    }

    public String getAccountHolder() {
        return accountHolder;
    }

    public boolean isAccountVerified() {
        return accountVerified;
    }

    public String getReturnZipcode() {
        return returnZipcode;
    }

    public String getReturnAddress1() {
        return returnAddress1;
    }

    public String getReturnAddress2() {
        return returnAddress2;
    }

    public int getPenaltyCount() {
        return penaltyCount;
    }

    public String getStatus() {
        return status;
    }
}
