package com.gongguhan.domain.notification.repository;

import com.gongguhan.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 동일 이벤트 중복 알림 방지(S15P21B205-110): dedup 키로 이미 저장된 알림이 있는지 확인. */
    boolean existsByDedupKey(String dedupKey);
}
