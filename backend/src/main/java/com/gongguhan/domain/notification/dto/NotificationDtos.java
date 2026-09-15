package com.gongguhan.domain.notification.dto;

import com.gongguhan.domain.notification.entity.Notification;
import java.time.LocalDateTime;

/** 알림 API(NOTI-01~03) 응답 DTO. */
public final class NotificationDtos {

    private NotificationDtos() {
    }

    /** NOTI-01 알림 한 건. */
    public record NotificationResponse(Long notificationId, String type, String title, String body,
                                       String linkUrl, boolean read, LocalDateTime createdAt) {

        public static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getNotificationId(), n.getType(), n.getTitle(),
                    n.getBody(), n.getLinkUrl(), n.getReadAt() != null, n.getCreatedAt());
        }
    }

    /** NOTI-02 읽지 않은 알림 수. */
    public record UnreadCountResponse(long unreadCount) {
    }
}
