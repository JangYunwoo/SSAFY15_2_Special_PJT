package com.gongguhan.domain.account.repository;

import com.gongguhan.domain.account.entity.UserAddress;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {

    Optional<UserAddress> findByAddressIdAndUserId(Long addressId, Long userId);
}
