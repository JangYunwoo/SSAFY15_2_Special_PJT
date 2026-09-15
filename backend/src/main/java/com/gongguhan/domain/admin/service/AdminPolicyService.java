package com.gongguhan.domain.admin.service;

import com.gongguhan.domain.admin.dto.AdminDtos.PolicyResponse;
import com.gongguhan.domain.admin.entity.AuditLog;
import com.gongguhan.domain.admin.repository.AuditLogRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import com.gongguhan.global.policy.PlatformPolicy;
import com.gongguhan.global.policy.PlatformPolicyRepository;
import com.gongguhan.global.policy.PolicyKey;
import com.gongguhan.global.policy.PolicyService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 관리자 운영 정책 조회·변경 (ADM-03·04).
 *
 * <p>변경은 {@link PolicyService#update}에 위임해 DB 반영과 캐시 무효화를 함께 처리하고,
 * 변경 사실을 {@code audit_logs}에 남긴다(FR-ADM-13).
 */
@Service
public class AdminPolicyService {

    private static final String TARGET_POLICY = "POLICY";
    private static final String ACTION_UPDATE = "POLICY_UPDATE";

    private final PlatformPolicyRepository platformPolicyRepository;
    private final PolicyService policyService;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AdminPolicyService(PlatformPolicyRepository platformPolicyRepository,
                              PolicyService policyService,
                              AuditLogRepository auditLogRepository,
                              ObjectMapper objectMapper) {
        this.platformPolicyRepository = platformPolicyRepository;
        this.policyService = policyService;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<PolicyResponse> getPolicies() {
        return platformPolicyRepository.findAll().stream().map(PolicyResponse::from).toList();
    }

    public PolicyResponse updatePolicy(String policyKey, String value, Long adminId) {
        PolicyKey key = parseKey(policyKey);
        PlatformPolicy before = platformPolicyRepository.findById(policyKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "정책값을 찾을 수 없습니다: %s".formatted(policyKey)));
        String oldValue = before.getPolicyValue();

        // DB 반영 + 캐시 무효화(자체 트랜잭션).
        policyService.update(key, value, adminId);
        auditLogRepository.save(AuditLog.record(AuditLog.ACTOR_ADMIN, adminId, TARGET_POLICY, null,
                ACTION_UPDATE, valueJson(oldValue), valueJson(value)));

        return PolicyResponse.from(platformPolicyRepository.findById(policyKey).orElseThrow());
    }

    private PolicyKey parseKey(String policyKey) {
        try {
            return PolicyKey.valueOf(policyKey);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "알 수 없는 정책 키입니다: %s".formatted(policyKey));
        }
    }

    private String valueJson(String value) {
        return objectMapper.writeValueAsString(Map.of("value", value));
    }
}
