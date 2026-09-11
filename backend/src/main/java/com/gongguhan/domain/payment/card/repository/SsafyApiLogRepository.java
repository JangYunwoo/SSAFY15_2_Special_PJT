package com.gongguhan.domain.payment.card.repository;

import com.gongguhan.domain.payment.card.entity.SsafyApiLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SsafyApiLogRepository extends JpaRepository<SsafyApiLog, Long> {

    boolean existsByApiServiceCodeAndInstitutionTxNo(String apiServiceCode, String institutionTxNo);
}
