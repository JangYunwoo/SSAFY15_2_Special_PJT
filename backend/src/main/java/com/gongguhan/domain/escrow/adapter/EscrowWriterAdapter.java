package com.gongguhan.domain.escrow.adapter;

import com.gongguhan.domain.escrow.entity.Escrow;
import com.gongguhan.domain.escrow.repository.EscrowRepository;
import com.gongguhan.domain.payment.common.port.EscrowWriterPort;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/** 결제 공통 계층의 보관 명령을 에스크로 JPA 엔티티 생성으로 연결한다. */
@Component
public class EscrowWriterAdapter implements EscrowWriterPort {

    private final EscrowRepository escrowRepository;

    public EscrowWriterAdapter(EscrowRepository escrowRepository) {
        this.escrowRepository = escrowRepository;
    }

    @Override
    public void hold(Long participationId, Long groupBuyId, long amount, LocalDateTime heldAt) {
        escrowRepository.save(Escrow.hold(participationId, groupBuyId, amount, heldAt));
    }
}
