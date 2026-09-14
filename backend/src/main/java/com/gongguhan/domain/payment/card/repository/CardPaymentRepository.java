package com.gongguhan.domain.payment.card.repository;

import com.gongguhan.domain.payment.card.entity.CardPayment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardPaymentRepository extends JpaRepository<CardPayment, Long> {
}
