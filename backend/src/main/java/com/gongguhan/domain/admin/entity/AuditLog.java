package com.gongguhan.domain.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 감사 로그(audit_logs). 관리자의 정책·상태 변경 등을 기록한다(ADM-07 조회 대상).
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    public static final String ACTOR_ADMIN = "ADMIN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @Column(name = "actor_type", nullable = false, length = 10)
    private String actorType;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "before_json", columnDefinition = "json")
    private String beforeJson;

    @Column(name = "after_json", columnDefinition = "json")
    private String afterJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AuditLog() {
    }

    private AuditLog(String actorType, Long actorId, String targetType, Long targetId,
                     String action, String beforeJson, String afterJson) {
        this.actorType = actorType;
        this.actorId = actorId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.action = action;
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
        this.createdAt = LocalDateTime.now();
    }

    public static AuditLog record(String actorType, Long actorId, String targetType, Long targetId,
                                  String action, String beforeJson, String afterJson) {
        return new AuditLog(actorType, actorId, targetType, targetId, action, beforeJson, afterJson);
    }

    public Long getLogId() {
        return logId;
    }

    public String getActorType() {
        return actorType;
    }

    public Long getActorId() {
        return actorId;
    }

    public String getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public String getAction() {
        return action;
    }

    public String getBeforeJson() {
        return beforeJson;
    }

    public String getAfterJson() {
        return afterJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
