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
 *
 * <p>두 소비자를 함께 섬긴다: 상품·공동구매 쪽의 판매 가능 여부 확인
 * ({@code existsBySellerIdAndStatusIn} - 원래 @Immutable 읽기 모델이었다)과
 * S15P21B205-63의 정산 계좌·반품 주소 조회/수정(SEL-06·07). 수정 경로가 생기면서
 * @Immutable은 제거했다. 판매자 전환(SEL-05)·인증(SEL-01~04) 생성 경로는 여기서
 * 다루지 않는다.
 */
@Entity
@Table(name = "seller_profiles")
public class SellerProfile extends BaseTimeEntity {

    /** 정상 판매 가능. */
    public static final String STATUS_ACTIVE = "ACTIVE";
    /** 위반 누적으로 신규 등록·공동구매 생성 차단. 기존 거래 처리는 계속 허용. */
    public static final String STATUS_SUSPENDED = "SUSPENDED";

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

    /** {@code ACTIVE} / {@code SUSPENDED}. 정지 판정은 제재 도메인이 내립니다. */
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

    /**
     * 위반 확정을 반영합니다 (S15P21B205-83, FR-PEN-07~10).
     *
     * <p>누적이 임계에 도달하면 판매를 정지합니다. 정지된 판매자는 신규 상품 등록과
     * 공동구매 생성이 막히지만({@code ProductService}·{@code GroupBuyService} 가
     * {@code SELLER_SUSPENDED} 로 거부), <b>기존 거래의 배송·반품·정산은 계속
     * 진행됩니다</b> — 이미 돈을 낸 구매자를 볼모로 잡지 않기 위해서입니다.
     *
     * @param limit 정지 임계 횟수. 정책값 {@code PENALTY_LIMIT} 입니다.
     */
    public void recordPenalty(int limit) {
        this.penaltyCount++;
        if (this.penaltyCount >= limit) {
            this.status = STATUS_SUSPENDED;
        }
    }

    /**
     * 확정 취소로 누적을 되돌립니다 (S15P21B205-83, FR-PEN-13).
     *
     * <p>잘못 확정한 건을 취소했는데 정지가 그대로면, 관리자가 실수를 바로잡아도
     * 판매자는 계속 막혀 있습니다. 임계 아래로 내려오면 정지를 풉니다.
     */
    public void revokePenalty(int limit) {
        if (this.penaltyCount > 0) {
            this.penaltyCount--;
        }
        if (this.penaltyCount < limit && STATUS_SUSPENDED.equals(this.status)) {
            this.status = STATUS_ACTIVE;
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
