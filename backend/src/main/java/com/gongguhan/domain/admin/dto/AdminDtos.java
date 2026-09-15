package com.gongguhan.domain.admin.dto;

import com.gongguhan.domain.admin.entity.AuditLog;
import com.gongguhan.domain.payment.card.entity.SsafyApiLog;
import com.gongguhan.global.policy.PlatformPolicy;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

/** 관리자 정책·로그 API(ADM-03·04·05·07) 응답·요청 DTO 모음. */
public final class AdminDtos {

    private AdminDtos() {
    }

    /** ADM-03 정책값 한 행. */
    public record PolicyResponse(String policyKey, String policyValue, String description,
                                 Long updatedBy, LocalDateTime updatedAt) {

        public static PolicyResponse from(PlatformPolicy policy) {
            return new PolicyResponse(policy.getPolicyKey(), policy.getPolicyValue(),
                    policy.getDescription(), policy.getUpdatedBy(), policy.getUpdatedAt());
        }
    }

    /** ADM-04 정책값 변경 요청. */
    public record PolicyUpdateRequest(@NotBlank String value) {
    }

    /** ADM-05 외부 금융 API 호출 이력 한 건. */
    public record ExternalApiLogResponse(Long logId, String apiServiceCode, String institutionTxNo,
                                         String targetType, Long targetId, String responseCode,
                                         String responseMessage, LocalDateTime calledAt) {

        public static ExternalApiLogResponse from(SsafyApiLog log) {
            return new ExternalApiLogResponse(log.getLogId(), log.getApiServiceCode(),
                    log.getInstitutionTxNo(), log.getTargetType(), log.getTargetId(),
                    log.getResponseCode(), log.getResponseMessage(), log.getCalledAt());
        }
    }

    /** ADM-07 감사 로그 한 건. */
    public record AuditLogResponse(Long logId, String actorType, Long actorId, String targetType,
                                   Long targetId, String action, String beforeJson, String afterJson,
                                   LocalDateTime createdAt) {

        public static AuditLogResponse from(AuditLog log) {
            return new AuditLogResponse(log.getLogId(), log.getActorType(), log.getActorId(),
                    log.getTargetType(), log.getTargetId(), log.getAction(),
                    log.getBeforeJson(), log.getAfterJson(), log.getCreatedAt());
        }
    }
}
