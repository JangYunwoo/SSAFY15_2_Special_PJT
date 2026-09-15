package com.gongguhan.domain.notification.repository;

import com.gongguhan.domain.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 동일 이벤트 중복 알림 방지(S15P21B205-110): dedup 키로 이미 저장된 알림이 있는지 확인. */
    boolean existsByDedupKey(String dedupKey);

    /** NOTI-01 내 알림 목록(최신순). 통합 계정이라 recipient_id가 곧 user_id다. */
    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    /** NOTI-02 읽지 않은 알림 수. */
    long countByRecipientIdAndReadAtIsNull(Long recipientId);
}
