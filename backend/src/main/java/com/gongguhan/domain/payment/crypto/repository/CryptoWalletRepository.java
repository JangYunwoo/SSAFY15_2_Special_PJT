package com.gongguhan.domain.payment.crypto.repository;

import com.gongguhan.domain.payment.crypto.entity.CryptoWallet;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CryptoWalletRepository extends JpaRepository<CryptoWallet, Long> {

    /**
     * 체인·용도별 활성 지갑 하나.
     *
     * <p>같은 용도의 활성 지갑이 둘 이상이면 아무거나 잡힌다. 수취 지갑을 교체할 때는
     * 새 지갑을 켜기 전에 기존 지갑을 비활성으로 내려야 한다. 두 개가 동시에 켜져 있으면
     * 입금이 양쪽으로 흩어져 스캔 대상이 갈린다.
     */
    Optional<CryptoWallet> findFirstByChainIdAndPurposeAndActiveTrue(long chainId, String purpose);
}
