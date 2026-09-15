package com.gongguhan.domain.notification.service;

import com.gongguhan.domain.notification.dto.NotificationDtos.NotificationResponse;
import com.gongguhan.domain.notification.entity.Notification;
import com.gongguhan.domain.notification.repository.NotificationRepository;
import com.gongguhan.domain.penalty.event.SanctionLevel;
import com.gongguhan.domain.penalty.event.SellerSanctionedEvent;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 플랫폼 거래 알림 저장. 현재는 판매자 제재 알림 소비(S15P21B205-110)를 담당한다.
 *
 * <p>제재 정책 결정은 penalty 도메인(S15P21B205-83)의 책임이고, 여기서는
 * {@link SellerSanctionedEvent} 계약만 보고 판매자에게 보낼 알림을 만든다.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** PEN-01. 판매자가 제재 알림에서 위반 이력으로 바로 이동할 수 있는 링크. */
    private static final String SELLER_PENALTY_LINK = "/sellers/me/penalties";

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /** NOTI-01 내 알림 목록. 통합 계정이라 recipientId가 곧 userId다. */
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getMyNotifications(Long userId, Pageable pageable) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId, pageable)
                .map(NotificationResponse::from);
    }

    /** NOTI-02 읽지 않은 알림 수. */
    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndReadAtIsNull(userId);
    }

    /** NOTI-03 알림 읽음 처리(본인 것만). */
    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "알림을 찾을 수 없습니다."));
        if (!notification.getRecipientId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_ACCESS_DENIED);
        }
        notification.markRead(LocalDateTime.now());
    }

    /**
     * 판매자 제재 이벤트를 알림으로 저장한다. 동일 제재({@code penaltyId})가 재발행·중복
     * 소비되어도 알림은 한 번만 쌓이도록 dedup 키로 멱등성을 보장한다.
     *
     * @return 새 알림을 저장했으면 true, 이미 존재해 건너뛰었으면 false
     */
    @Transactional
    public boolean notifySellerSanction(SellerSanctionedEvent event) {
        String dedupKey = dedupKey(event);
        if (notificationRepository.existsByDedupKey(dedupKey)) {
            log.debug("판매자 제재 알림 중복으로 건너뜀: dedupKey={}", dedupKey);
            return false;
        }

        Notification notification = Notification.create(
                Notification.RECIPIENT_SELLER,
                event.sellerId(),
                typeOf(event.level()),
                titleOf(event.level()),
                bodyOf(event),
                SELLER_PENALTY_LINK,
                dedupKey);

        try {
            notificationRepository.save(notification);
        } catch (DataIntegrityViolationException duplicate) {
            // 유니크 인덱스로 동시 소비 경쟁까지 방어한다(존재 확인과 저장 사이의 경합).
            log.debug("판매자 제재 알림 동시 저장 경쟁 감지, 멱등 처리: dedupKey={}", dedupKey);
            return false;
        }
        log.info("판매자 제재 알림 저장: sellerId={} level={} penaltyId={}",
                event.sellerId(), event.level(), event.penaltyId());
        return true;
    }

    private String dedupKey(SellerSanctionedEvent event) {
        return "SELLER_SANCTION:" + event.penaltyId() + ":" + event.level();
    }

    private String typeOf(SanctionLevel level) {
        return switch (level) {
            case WARNING -> Notification.TYPE_SELLER_SANCTION_WARNING;
            case SUSPENDED -> Notification.TYPE_SELLER_SANCTION_SUSPENDED;
            case THREE_STRIKE -> Notification.TYPE_SELLER_SANCTION_THREE_STRIKE;
        };
    }

    private String titleOf(SanctionLevel level) {
        return switch (level) {
            case WARNING -> "판매자 경고 안내";
            case SUSPENDED -> "판매 정지 안내";
            case THREE_STRIKE -> "쓰리아웃 판매 정지 안내";
        };
    }

    private String bodyOf(SellerSanctionedEvent event) {
        return switch (event.level()) {
            case WARNING -> "위반이 확인되어 경고가 부과되었습니다. (누적 " + event.violationCount() + "회)";
            case SUSPENDED -> "위반이 확인되어 판매가 정지되었습니다. (누적 " + event.violationCount() + "회)";
            case THREE_STRIKE -> "위반 3회 누적으로 신규 상품 등록과 공동구매 생성이 제한됩니다. "
                    + "진행 중인 거래의 배송·반품·정산은 계속 처리됩니다.";
        };
    }
}
