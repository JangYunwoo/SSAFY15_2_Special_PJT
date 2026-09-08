package com.gongguhan.domain.escrow.repository;

import com.gongguhan.domain.escrow.entity.Escrow;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EscrowRepository extends JpaRepository<Escrow, Long> {
}
