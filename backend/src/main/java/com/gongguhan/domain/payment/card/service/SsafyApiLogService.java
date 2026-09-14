package com.gongguhan.domain.payment.card.service;

import com.gongguhan.domain.payment.card.entity.SsafyApiLog;
import com.gongguhan.domain.payment.card.repository.SsafyApiLogRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code ssafy_api_logs} 테이블 전용 - 기관거래고유번호 채번과 호출 이력 기록을 함께 둔다.
 * 두 책임 모두 같은 테이블(유일성 제약, 이력 조회)에 묶여 있고, 카드 결제 승인
 * ({@code createCreditCardTransaction})뿐 아니라 카드 결제 취소({@code deleteTransaction},
 * S15P21B205-108)도 같은 패키지에서 이 클래스를 재사용할 예정이다.
 */
@Component
public class SsafyApiLogService {

    private static final int MAX_GENERATION_ATTEMPTS = 5;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmmss");

    private final SsafyApiLogRepository ssafyApiLogRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SsafyApiLogService(SsafyApiLogRepository ssafyApiLogRepository, ObjectMapper objectMapper, Clock clock) {
        this.ssafyApiLogRepository = ssafyApiLogRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * {@code institutionTransactionUniqueNo}를 채번한다: {@code YYYYMMDD(8)+HHMMSS(6)+난수(6)} = 20자.
     *
     * <p>사전 중복 체크 후 통과한 후보를 반환하는 방식이라, 이론적으로 같은 초·같은
     * apiServiceCode로 두 요청이 동시에 같은 후보를 통과시키는 경합(TOCTOU)이 있을 수
     * 있다. 6자리 난수가 충돌할 확률은 무시할 수준이고, 정말 충돌하면 저장 시점의
     * {@code uk_ssafy_tx_no} 유니크 제약이 최종 방어선이 된다 - 이 정도 희귀 경합에
     * 별도 트랜잭션 재시도까지 두는 것은 과설계로 보고 생략했다.
     *
     * @throws BusinessException {@code INTERNAL_SERVER_ERROR} - 재시도를 모두 소진한 경우
     *         (사실상 발생하지 않아야 정상)
     */
    public String generateInstitutionTransactionUniqueNo(String apiServiceCode) {
        LocalDateTime now = LocalDateTime.now(clock);
        String prefix = now.format(DATE_FORMAT) + now.format(TIME_FORMAT);
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String candidate = prefix + "%06d".formatted(ThreadLocalRandom.current().nextInt(1_000_000));
            if (!ssafyApiLogRepository.existsByApiServiceCodeAndInstitutionTxNo(apiServiceCode, candidate)) {
                return candidate;
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "기관거래고유번호를 생성하지 못했습니다.");
    }

    /**
     * 호출 성공·실패와 무관하게 항상 남긴다.
     *
     * @param sanitizedRequest 카드번호·CVC 등 민감정보를 뺀 요청 스냅샷(로그용 별도 객체)
     * @param rawResponse      SSAFY 응답 원본(또는 응답 자체가 없었다면 {@code null})
     */
    @Transactional
    public void record(String apiServiceCode, String institutionTxNo, String targetType, Long targetId,
                        Object sanitizedRequest, String responseCode, String responseMessage, Object rawResponse) {
        SsafyApiLog entry = SsafyApiLog.create(
                apiServiceCode, institutionTxNo, LocalDateTime.now(clock).format(DATE_FORMAT),
                targetType, targetId, writeJson(sanitizedRequest),
                responseCode, responseMessage, writeJson(rawResponse),
                LocalDateTime.now(clock));
        ssafyApiLogRepository.save(entry);
    }

    private String writeJson(Object value) {
        return value == null ? null : objectMapper.writeValueAsString(value);
    }
}
