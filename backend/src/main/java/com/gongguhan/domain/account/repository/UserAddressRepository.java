package com.gongguhan.domain.account.repository;

import com.gongguhan.domain.account.entity.UserAddress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {

    Optional<UserAddress> findByAddressIdAndUserId(Long addressId, Long userId);

    List<UserAddress> findByUserIdOrderByIsDefaultDescAddressIdAsc(Long userId);

    Optional<UserAddress> findByUserIdAndIsDefaultTrue(Long userId);
}
