package com.gongguhan.domain.payment.crypto.repository;

import com.gongguhan.domain.payment.crypto.entity.CryptoAsset;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CryptoAssetRepository extends JpaRepository<CryptoAsset, Long> {

    /** CRYP-01 지원 자산 목록. 비활성 자산은 노출하지 않는다. */
    List<CryptoAsset> findByEnabledTrueOrderByAssetIdAsc();

    /** Quote 발급 시 사용. 비활성 자산으로는 견적을 낼 수 없다. */
    Optional<CryptoAsset> findByAssetIdAndEnabledTrue(Long assetId);

    /** 입금 로그에는 자산 ID 가 없고 체인·토큰 주소만 있다. 그걸로 자산을 되찾는다. */
    Optional<CryptoAsset> findByChainIdAndTokenContractIgnoreCase(long chainId, String tokenContract);
}
