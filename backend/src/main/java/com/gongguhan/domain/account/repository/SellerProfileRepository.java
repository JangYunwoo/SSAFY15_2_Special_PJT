package com.gongguhan.domain.account.repository;

import com.gongguhan.domain.account.entity.SellerProfile;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerProfileRepository extends JpaRepository<SellerProfile, Long> {

    boolean existsBySellerIdAndStatusIn(Long sellerId, Collection<String> statuses);
}
