package com.gongguhan.domain.admin.service;

import com.gongguhan.domain.admin.entity.AuditLog;
import com.gongguhan.domain.admin.repository.AuditLogRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 관리자 조작을 감사 로그에 남깁니다 (S15P21B205-60, FR-ADM-06, ADM-07 조회 대상).
 *
 * <h2>무엇을 남기는가</h2>
 *
 * <p><b>관리자가 돈이나 사람의 권리에 손을 대는 지점</b>입니다. 정산 이체는 실제로 돈이
 * 나가고, 제재 확정은 판매자의 판매를 막고, 환불 재처리는 돈을 다시 내보냅니다. 나중에
 * "누가 언제 이걸 했나" 를 답할 수 없으면 분쟁에서 아무것도 증명하지 못합니다.
 *
 * <p>조회는 남기지 않습니다. 상태를 바꾸지 않는 동작까지 쌓으면 정작 중요한 변경이
 * 묻힙니다.
 *
 * <h2>왜 공용 서비스인가</h2>
 *
 * <p>각 도메인이 {@code AuditLogRepository} 와 {@code ObjectMapper} 를 직접 들고 있으면
 * JSON 직렬화 방식이 제각각이 되고, 나중에 형식을 바꿀 때 모든 호출부를 고쳐야 합니다.
 * 기록 방법은 여기 한 곳에만 둡니다.
 *
 * <h2>트랜잭션</h2>
 *
 * <p>호출자의 트랜잭션에 참여합니다({@code REQUIRED}). <b>업무가 롤백되면 감사 로그도
 * 함께 사라져야 합니다</b> — 일어나지 않은 일을 기록으로 남기면 로그가 거짓이 됩니다.
 * 트랜잭션 밖에서 부르면 이 메서드가 자체 트랜잭션으로 짧게 기록합니다.
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    /** {@code audit_logs.target_type}. 컬럼이 30자라 짧게 유지합니다. */
    public static final String TARGET_SELLER_PENALTY = "SELLER_PENALTY";
    public static final String TARGET_SETTLEMENT = "SETTLEMENT";
    public static final String TARGET_REFUND = "REFUND";

    /** {@code audit_logs.action}. 컬럼이 50자입니다. */
    public static final String ACTION_CONFIRM = "CONFIRM";
    public static final String ACTION_DISMISS = "DISMISS";
    public static final String ACTION_REVOKE = "REVOKE";
    public static final String ACTION_TRANSFER = "TRANSFER";
    public static final String ACTION_HOLD = "HOLD";
    public static final String ACTION_RELEASE_HOLD = "RELEASE_HOLD";
    public static final String ACTION_RETRY = "RETRY";

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 관리자 조작을 기록합니다.
     *
     * <p>{@code before} 와 {@code after} 는 <b>바뀐 값만</b> 담습니다. 엔티티 전체를 넣으면
     * 무엇이 달라졌는지 읽는 사람이 직접 비교해야 하고, 로그가 불필요하게 커집니다.
     *
     * @param adminId    조작한 관리자. 인증에서 넘어온 값입니다
     * @param targetType {@code TARGET_*} 상수
     * @param targetId   대상 식별자
     * @param action     {@code ACTION_*} 상수
     * @param before     변경 전 값. 생성처럼 이전 상태가 없으면 {@code null}
     * @param after      변경 후 값. 삭제처럼 이후 상태가 없으면 {@code null}
     */
    @Transactional
    public void record(Long adminId, String targetType, Long targetId, String action,
                       Map<String, ?> before, Map<String, ?> after) {
        auditLogRepository.save(AuditLog.record(AuditLog.ACTOR_ADMIN, adminId,
                targetType, targetId, action, toJson(before), toJson(after)));
    }

    /**
     * 감사 로그 값을 만듭니다. <b>{@code Map.of} 대신 이것을 쓰세요.</b>
     *
     * <p>{@code Map.of} 는 값이 하나라도 {@code null} 이면 NPE 를 던집니다. 그러면 감사
     * 로그 때문에 정산 이체나 제재 확정 같은 본 업무가 실패합니다. 아직 채워지지 않은
     * 필드는 흔하므로, 여기서는 {@code null} 인 항목을 조용히 건너뜁니다.
     *
     * @param keyValues 키와 값을 번갈아 넘깁니다
     */
    public static Map<String, Object> values(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("키와 값을 짝으로 넘겨야 합니다.");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            Object value = keyValues[i + 1];
            if (value != null) {
                map.put(String.valueOf(keyValues[i]), value);
            }
        }
        return map;
    }

    /**
     * {@code Map} 을 JSON 문자열로 바꿉니다.
     *
     * <p>직렬화가 실패해도 예외를 올리지 않습니다. 감사 로그 때문에 정산 이체나 제재
     * 확정 같은 본 업무가 실패하면 안 됩니다. 값을 못 남기더라도 <b>"누가 무엇을 했다"
     * 는 사실은 남는 쪽</b>이 낫습니다.
     */
    private String toJson(Map<String, ?> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            log.warn("감사 로그 값을 직렬화하지 못했습니다. 값 없이 기록합니다. reason={}",
                    exception.getMessage());
            return null;
        }
    }
}
