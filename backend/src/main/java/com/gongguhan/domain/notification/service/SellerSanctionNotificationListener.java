package com.gongguhan.domain.notification.service;

import com.gongguhan.domain.penalty.event.SellerSanctionedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 판매자 제재 이벤트를 소비해 알림을 저장한다(S15P21B205-110).
 *
 * <p><b>AFTER_COMMIT인 이유</b>: 제재 확정 트랜잭션이 실제로 커밋된 뒤에만 알림을
 * 남겨야 한다. 판정이 롤백되면 알림도 없어야 하므로, 발행 즉시 실행되는 일반
 * {@code @EventListener} 대신 커밋 이후 단계에서 소비한다. 알림 저장 실패가 제재
 * 확정을 되돌리지 않도록 소비는 별도 트랜잭션({@link NotificationService#notifySellerSanction})
 * 에서 이뤄진다.
 */
@Component
public class SellerSanctionNotificationListener {

    private final NotificationService notificationService;

    public SellerSanctionNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSellerSanctioned(SellerSanctionedEvent event) {
        notificationService.notifySellerSanction(event);
    }
}
