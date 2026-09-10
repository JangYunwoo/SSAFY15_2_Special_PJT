package com.gongguhan.domain.product.repository;

import com.gongguhan.domain.product.entity.WithdrawalRestrictionReason;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WithdrawalRestrictionReasonRepository
        extends JpaRepository<WithdrawalRestrictionReason, Long> {

    List<WithdrawalRestrictionReason> findByActiveTrueOrderBySortOrderAscReasonIdAsc();
}
