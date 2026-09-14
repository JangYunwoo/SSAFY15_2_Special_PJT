package com.gongguhan.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 인앱 알림 (notifications).
 *
 * <p>배송 알림은 다루지 않는다(택배사 수행). 모집 완료·실패, 환불, 정산, 제재 등
 * 플랫폼 거래 알림만 저장한다.
 *
 * <p>{@code dedupKey}는 하나의 도메인 이벤트가 재발행·중복 소비되어도 같은 알림이
 * 두 번 쌓이지 않도록 하는 멱등 키다. dedup이 필요 없는 알림은 null로 둔다(유니크
 * 인덱스는 다중 null을 허용).
 */
@Entity
@Table(name = "notifications")
public class Notification {

    public static final String RECIPIENT_BUYER = "BUYER";
    public static final String RECIPIENT_SELLER = "SELLER";

    public static final String TYPE_SELLER_SANCTION_WARNING = "SELLER_SANCTION_WARNING";
    public static final String TYPE_SELLER_SANCTION_SUSPENDED = "SELLER_SANCTION_SUSPENDED";
    public static final String TYPE_SELLER_SANCTION_THREE_STRIKE = "SELLER_SANCTION_THREE_STRIKE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @Column(name = "recipient_type", nullable = false, length = 10)
    private String recipientType;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 500)
    private String body;

    @Column(name = "link_url", length = 255)
    private String linkUrl;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "dedup_key", length = 150)
    private String dedupKey;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Notification() {
    }

    private Notification(String recipientType, Long recipientId, String type, String title,
                         String body, String linkUrl, String dedupKey) {
        this.recipientType = recipientType;
        this.recipientId = recipientId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.linkUrl = linkUrl;
        this.dedupKey = dedupKey;
    }

    public static Notification create(String recipientType, Long recipientId, String type,
                                      String title, String body, String linkUrl, String dedupKey) {
        return new Notification(recipientType, recipientId, type, title, body, linkUrl, dedupKey);
    }

    public void markRead(LocalDateTime readAt) {
        if (this.readAt == null) {
            this.readAt = readAt;
        }
    }

    public Long getNotificationId() {
        return notificationId;
    }

    public String getRecipientType() {
        return recipientType;
    }

    public Long getRecipientId() {
        return recipientId;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getLinkUrl() {
        return linkUrl;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
