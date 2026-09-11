package com.gongguhan.domain.escrow.adapter;

import com.gongguhan.domain.escrow.entity.Escrow;
import com.gongguhan.domain.escrow.repository.EscrowRepository;
import com.gongguhan.domain.payment.common.port.EscrowWriterPort;
import java.time.LocalDateTime;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * {@link EscrowWriterPort}의 테스트 전용 임시 구현체.
 *
 * <p><b>실제 어댑터가 아니다.</b> {@code src/main}에는 이 포트의 구현체가 없다 -
 * S15P21B205-106(최영우님 티켓)이 만든다. 이 클래스는 {@code src/test} 전용이라
 * 프로덕션 빌드에 포함되지 않으며, S15P21B205-103이 payment.common을 포트로
 * 마이그레이션한 뒤에도 기존 통합테스트가 Spring 컨텍스트를 띄울 수 있게 하기
 * 위한 최소 픽스처다. -106이 실제 어댑터를 만들면 이 파일은 삭제한다.
 */
@Component
@Profile("test")
public class EscrowWriterTestAdapter implements EscrowWriterPort {

    private final EscrowRepository escrowRepository;

    public EscrowWriterTestAdapter(EscrowRepository escrowRepository) {
        this.escrowRepository = escrowRepository;
    }

    @Override
    public void hold(Long participationId, Long groupBuyId, long amount, LocalDateTime heldAt) {
        escrowRepository.save(Escrow.hold(participationId, groupBuyId, amount, heldAt));
    }
}
