package com.gongguhan.domain.payment.crypto.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 플랫폼 지갑.
 *
 * <p><b>개인키는 이 테이블에 절대 넣지 않는다.</b> 주소만 보관하고, 서명이 필요한
 * 환불 송금(RFD)은 환경변수로 주입한 개인키를 쓴다. DB가 유출되어도 자금이
 * 빠져나가지 않게 하기 위한 분리다.
 *
 * <p>수취({@code RECEIVE}) 지갑은 서버가 서명할 일이 없어 개인키가 아예 필요 없다.
 */
@Entity
@Table(name = "crypto_wallets")
public class CryptoWallet {

    /** 구매자 결제를 받는 지갑. */
    public static final String PURPOSE_RECEIVE = "RECEIVE";
    /** 환불 송금용 지갑. */
    public static final String PURPOSE_REFUND = "REFUND";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "wallet_id")
    private Long walletId;

    @Column(name = "chain_id", nullable = false)
    private long chainId;

    @Column(nullable = false, length = 42)
    private String address;

    @Column(nullable = false, length = 10)
    private String purpose;

    @Column(nullable = false)
    private boolean active;

    @Column(length = 255)
    private String memo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected CryptoWallet() {
    }

    /**
     * 실제 배포 주소가 채워졌는지 확인한다.
     *
     * <p>V1 시드는 영주소({@code 0x000...0})를 넣어 둔다. 그 상태로 Quote를 내주면
     * 구매자가 소각 주소로 송금하게 되므로, 발급 전에 반드시 막아야 한다.
     */
    public boolean isPlaceholder() {
        return address == null || address.replace("0x", "").chars().allMatch(c -> c == '0');
    }

    public Long getWalletId() {
        return walletId;
    }

    public long getChainId() {
        return chainId;
    }

    public String getAddress() {
        return address;
    }

    public String getPurpose() {
        return purpose;
    }

    public boolean isActive() {
        return active;
    }
}
