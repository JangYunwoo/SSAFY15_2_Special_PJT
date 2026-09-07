package com.gongguhan.domain.account.repository;

import com.gongguhan.domain.account.entity.UserAddress;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {
}
