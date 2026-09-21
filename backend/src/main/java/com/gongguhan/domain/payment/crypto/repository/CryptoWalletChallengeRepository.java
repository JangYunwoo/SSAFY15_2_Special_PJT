package com.gongguhan.domain.payment.crypto.repository;

import com.gongguhan.domain.payment.crypto.entity.CryptoWalletChallenge;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CryptoWalletChallengeRepository extends JpaRepository<CryptoWalletChallenge, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CryptoWalletChallenge c where c.challengeId = :id")
    Optional<CryptoWalletChallenge> findForUpdate(@Param("id") String id);
}
