package com.gongguhan.domain.refund.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gongguhan.domain.payment.card.service.SsafyProperties;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * SSAFY 교육용 금융망의 {@code updateDemandDepositAccountTransfer}(계좌 이체) 호출을 감싼다.
 *
 * <p><b>왜 환불에 계좌이체가 필요한가.</b> SSAFY 카드 취소
 * ({@code deleteTransaction})는 취소 요청마다 <b>카드번호와 CVC 를 다시 요구</b>합니다.
 * 카드 원문은 어디에도 저장하지 않으므로(보안 요건), 사람이 카드를 다시 입력하는
 * 화면이 없는 <b>자동 환불은 카드 취소를 쓸 수 없습니다</b> — 모집 실패 배치,
 * 반품 수거 완료, 참여 취소가 모두 여기 해당합니다. 그래서 계좌이체가 예외적
 * 폴백이 아니라 자동 환불의 <b>주 경로</b>입니다.
 *
 * <p>{@link com.gongguhan.domain.payment.card.service.SsafyCardApiClient} 와 같은 관례를
 * 따릅니다 — 인터페이스로 추상화하지 않은 concrete 빈이고, 테스트는 이 클래스 자체를
 * Mockito 로 mock 합니다.
 *
 * <p><b>H0000 이 아닌 응답도 예외로 던지지 않습니다.</b> 성공·실패와 무관하게 호출자가
 * {@code ssafy_api_logs} 에 기록해야 하므로 판정과 로깅은 호출자 책임입니다. 네트워크
 * 자체가 실패한 경우만 예외로 바꿉니다 — 그때는 응답이 없어 호출자가 판정할 재료가
 * 없기 때문입니다.
 *
 * <p><b>헤더 구성이 카드 클라이언트와 중복됩니다.</b> 카드 클라이언트는 결제 담당
 * 소유 파일이라 공통 추출을 이 MR 범위에서 하지 않았습니다. 정산(STL-04)도 같은
 * 이체 API 를 쓰므로, 그때 {@code SsafyApiHeaderFactory} 같은 공용 요소로 뽑는 것을
 * 팀과 합의하는 편이 좋습니다.
 */
@Component
public class SsafyTransferClient {

    private static final Logger log = LoggerFactory.getLogger(SsafyTransferClient.class);

    /** SSAFY API 서비스 코드. {@code ssafy_api_logs} 의 키이기도 합니다. */
    public static final String API_SERVICE_CODE = "updateDemandDepositAccountTransfer";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmmss");

    /** 정상 응답 코드. */
    public static final String RESPONSE_CODE_SUCCESS = "H0000";

    private final RestClient restClient;
    private final SsafyProperties properties;
    private final Clock clock;

    public SsafyTransferClient(SsafyProperties properties, Clock clock) {
        Duration timeout = Duration.ofMillis(properties.timeoutMs());
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        requestFactory.setReadTimeout(timeout);

        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 플랫폼 계좌에서 구매자 계좌로 환불금을 이체합니다.
     *
     * @param institutionTransactionUniqueNo 호출 <b>전에</b> 미리 채번된 값(H1007 중복 방지).
     *                                       {@code SsafyApiLogService} 가 생성합니다.
     *                                       재시도 시 같은 값을 다시 쓰면 금융망이 중복으로
     *                                       거부하므로, 재처리마다 새로 채번해야 합니다.
     * @param withdrawalAccountNo            출금 계좌(플랫폼). 정책값
     *                                       {@code SSAFY_PLATFORM_ACCOUNT_NO}.
     * @param depositAccountNo               입금 계좌(구매자). {@code users.ssafy_account_no}.
     * @param amount                         이체 금액(KRW).
     * @throws BusinessException {@code EXTERNAL_SERVICE_UNAVAILABLE} — 네트워크 자체가
     *         실패한 경우. <b>이때 이체가 실제로 일어났는지는 알 수 없습니다.</b>
     *         호출자는 자동 재시도 대신 사람이 확인하도록 남겨야 합니다.
     */
    public TransferResponse transfer(String institutionTransactionUniqueNo,
                                     String institutionCode, String fintechAppNo,
                                     String ssafyUserKey, String withdrawalAccountNo,
                                     String depositAccountNo, long amount,
                                     String withdrawalSummary, String depositSummary) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("Header", buildHeader(institutionTransactionUniqueNo, institutionCode,
                fintechAppNo, ssafyUserKey));
        body.put("depositAccountNo", depositAccountNo);
        body.put("depositTransactionSummary", depositSummary);
        body.put("transactionBalance", String.valueOf(amount));
        body.put("withdrawalAccountNo", withdrawalAccountNo);
        body.put("withdrawalTransactionSummary", withdrawalSummary);

        try {
            TransferResponse response = restClient.post()
                    .uri("/edu/demandDeposit/" + API_SERVICE_CODE)
                    .body(body)
                    .retrieve()
                    .body(TransferResponse.class);
            if (response == null || response.header() == null) {
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                        "SSAFY 계좌이체 응답이 비어 있습니다.");
            }
            return response;
        } catch (RestClientException exception) {
            // 응답을 받지 못했다. 이체가 일어났는지 알 수 없는 상태다.
            log.error("SSAFY 계좌이체 호출 실패 institutionTxNo={} amount={}",
                    institutionTransactionUniqueNo, amount, exception);
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "SSAFY 금융망 연동이 일시적으로 불가능합니다.");
        }
    }

    private Map<String, Object> buildHeader(String institutionTransactionUniqueNo,
                                            String institutionCode, String fintechAppNo,
                                            String ssafyUserKey) {
        LocalDateTime now = LocalDateTime.now(clock);
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("apiName", API_SERVICE_CODE);
        header.put("transmissionDate", now.format(DATE_FORMAT));
        header.put("transmissionTime", now.format(TIME_FORMAT));
        header.put("institutionCode", institutionCode);
        header.put("fintechAppNo", fintechAppNo);
        header.put("apiServiceCode", API_SERVICE_CODE);
        header.put("institutionTransactionUniqueNo", institutionTransactionUniqueNo);
        header.put("apiKey", properties.apiKey());
        header.put("userKey", ssafyUserKey);
        return header;
    }

    /**
     * 이체 응답.
     *
     * <p>{@code REC} 는 출금·입금 두 건의 거래 목록입니다({@code transactionType} 1=출금,
     * 2=입금). 환불 원장에는 대표값 하나만 남기면 되므로 첫 건의 거래고유번호를 씁니다.
     */
    public record TransferResponse(
            @JsonProperty("Header") Header header,
            @JsonProperty("REC") List<Record> rec
    ) {

        public boolean isSuccess() {
            return header != null && RESPONSE_CODE_SUCCESS.equals(header.responseCode());
        }

        /** 대표 거래고유번호. 응답에 거래 목록이 없으면 {@code null}. */
        public Long firstTransactionUniqueNo() {
            if (rec == null || rec.isEmpty()) {
                return null;
            }
            String value = rec.get(0).transactionUniqueNo();
            return value == null ? null : Long.valueOf(value);
        }

        public record Header(
                @JsonProperty("responseCode") String responseCode,
                @JsonProperty("responseMessage") String responseMessage,
                @JsonProperty("institutionTransactionUniqueNo") String institutionTransactionUniqueNo
        ) {
        }

        public record Record(
                @JsonProperty("transactionUniqueNo") String transactionUniqueNo,
                @JsonProperty("transactionDate") String transactionDate,
                @JsonProperty("transactionType") String transactionType,
                @JsonProperty("transactionAccountNo") String transactionAccountNo
        ) {
        }
    }
}
