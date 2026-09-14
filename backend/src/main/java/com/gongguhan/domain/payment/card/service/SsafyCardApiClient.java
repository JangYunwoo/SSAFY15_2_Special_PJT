package com.gongguhan.domain.payment.card.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * SSAFY 교육용 금융망의 {@code createCreditCardTransaction}(카드 결제) 호출을 감싼다.
 *
 * <p>{@link com.gongguhan.domain.payment.crypto.service.ChainClient}·
 * {@link com.gongguhan.domain.payment.crypto.service.UpbitExchangeRateProvider}와 같은
 * 관례를 따른다 - 인터페이스로 추상화하지 않은 concrete 빈이고, 테스트는 이 클래스
 * 자체를 Mockito로 mock한다.
 *
 * <p><b>H0000이 아닌 응답도 예외로 던지지 않는다.</b> 호출이 성공/실패했는지와 무관하게
 * 호출자({@code CardPaymentService})가 {@code ssafy_api_logs}에 항상 기록해야 하므로,
 * 판정과 로깅은 호출자 책임으로 남긴다({@link com.gongguhan.domain.payment.crypto.service.ChainClient}가
 * {@code ReceiptView.success}를 그냥 데이터로 돌려주는 것과 같은 이유). 네트워크 자체가
 * 실패한 경우(타임아웃, 연결 거부 등)만 여기서 예외로 변환한다 - 그 경우는 응답이 아예
 * 없어 호출자가 판정할 재료가 없기 때문이다.
 */
@Component
public class SsafyCardApiClient {

    private static final Logger log = LoggerFactory.getLogger(SsafyCardApiClient.class);
    private static final String API_SERVICE_CODE = "createCreditCardTransaction";
    public static final String CANCEL_API_SERVICE_CODE = "deleteTransaction";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmmss");

    private final RestClient restClient;
    private final SsafyProperties properties;

    public SsafyCardApiClient(SsafyProperties properties) {
        Duration timeout = Duration.ofMillis(properties.timeoutMs());
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        requestFactory.setReadTimeout(timeout);

        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
        this.properties = properties;
    }

    /**
     * 카드 결제를 승인 요청한다.
     *
     * @param institutionTransactionUniqueNo 호출 전에 미리 채번된 값(H1007 방지).
     *                                        {@code SsafyApiLogService}가 생성한다.
     * @param institutionCode                 기관코드 (SSAFY_INSTITUTION_CODE 정책값)
     * @param fintechAppNo                     핀테크 앱 일련번호 (SSAFY_FINTECH_APP_NO 정책값)
     * @param ssafyUserKey                    결제하는 구매자의 SSAFY 회원 userKey
     * @param cardNumber                      카드번호. 이 메서드를 벗어나면 어디에도 남지 않는다.
     * @param cvc                             카드 보안코드. 마찬가지로 저장·로깅되지 않는다.
     * @param merchantId                      공구한 가맹점 ID (SSAFY_MERCHANT_ID 정책값)
     * @param paymentBalance                  결제금액(KRW)
     * @throws BusinessException {@code EXTERNAL_SERVICE_UNAVAILABLE} - 네트워크 자체가 실패한 경우
     */
    public SsafyCardTransactionResponse createCreditCardTransaction(
            String institutionTransactionUniqueNo, String institutionCode, String fintechAppNo,
            String ssafyUserKey, String cardNumber, String cvc, long merchantId, long paymentBalance) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("Header", buildHeader(API_SERVICE_CODE,
                institutionTransactionUniqueNo, institutionCode, fintechAppNo, ssafyUserKey));
        body.put("cardNo", cardNumber);
        body.put("cvc", cvc);
        body.put("merchantId", String.valueOf(merchantId));
        body.put("paymentBalance", String.valueOf(paymentBalance));

        try {
            SsafyCardTransactionResponse response = restClient.post()
                    .uri("/creditCard/" + API_SERVICE_CODE)
                    .body(body)
                    .retrieve()
                    .body(SsafyCardTransactionResponse.class);
            if (response == null || response.header() == null) {
                // 카드번호는 요청 바디에만 담겨 응답이 비어도 로그에 남지 않는다.
                log.warn("SSAFY 카드 결제 응답이 비어 있음 institutionTxNo={}", institutionTransactionUniqueNo);
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                        "SSAFY 금융망 응답이 비어 있습니다.");
            }
            return response;
        } catch (RestClientException e) {
            log.warn("SSAFY 카드 결제 호출 실패 institutionTxNo={}", institutionTransactionUniqueNo, e);
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "SSAFY 금융망 연동에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    /**
     * 카드 결제를 취소 요청한다 (S15P21B205-34에서 확인한 제약: 금액 필드가 없어
     * {@code transactionUniqueNo} 단위 <b>전액 취소만</b> 가능하고, 청구서가 이미 발행된
     * 거래는 A1058로 거부된다).
     *
     * <p>성공/실패 판정과 로깅이 호출자 책임인 것은
     * {@link #createCreditCardTransaction}과 같다.
     *
     * @param transactionUniqueNo 원거래의 SSAFY 거래고유번호 ({@code card_payments.transaction_unique_no})
     */
    public SsafyCardTransactionResponse deleteTransaction(
            String institutionTransactionUniqueNo, String institutionCode, String fintechAppNo,
            String ssafyUserKey, String cardNumber, String cvc, long transactionUniqueNo) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("Header", buildHeader(CANCEL_API_SERVICE_CODE,
                institutionTransactionUniqueNo, institutionCode, fintechAppNo, ssafyUserKey));
        body.put("cardNo", cardNumber);
        body.put("cvc", cvc);
        body.put("transactionUniqueNo", String.valueOf(transactionUniqueNo));

        try {
            SsafyCardTransactionResponse response = restClient.post()
                    .uri("/creditCard/" + CANCEL_API_SERVICE_CODE)
                    .body(body)
                    .retrieve()
                    .body(SsafyCardTransactionResponse.class);
            if (response == null || response.header() == null) {
                log.warn("SSAFY 카드 취소 응답이 비어 있음 institutionTxNo={}", institutionTransactionUniqueNo);
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                        "SSAFY 금융망 응답이 비어 있습니다.");
            }
            return response;
        } catch (RestClientException e) {
            log.warn("SSAFY 카드 취소 호출 실패 institutionTxNo={}", institutionTransactionUniqueNo, e);
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "SSAFY 금융망 연동에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private Map<String, String> buildHeader(String apiServiceCode, String institutionTransactionUniqueNo,
                                             String institutionCode, String fintechAppNo, String ssafyUserKey) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, String> header = new LinkedHashMap<>();
        header.put("apiName", apiServiceCode);
        header.put("transmissionDate", now.format(DATE_FORMAT));
        header.put("transmissionTime", now.format(TIME_FORMAT));
        header.put("institutionCode", institutionCode);
        header.put("fintechAppNo", fintechAppNo);
        header.put("apiServiceCode", apiServiceCode);
        header.put("institutionTransactionUniqueNo", institutionTransactionUniqueNo);
        header.put("apiKey", properties.apiKey());
        header.put("userKey", ssafyUserKey);
        return header;
    }

    /** SSAFY {@code createCreditCardTransaction} 응답. REC 필드는 예제상 전부 문자열로 온다. */
    public record SsafyCardTransactionResponse(
            @JsonProperty("Header") Header header,
            @JsonProperty("REC") Rec rec
    ) {

        public boolean isSuccess() {
            return header != null && "H0000".equals(header.responseCode());
        }

        public record Header(
                @JsonProperty("responseCode") String responseCode,
                @JsonProperty("responseMessage") String responseMessage
        ) {
        }

        public record Rec(
                @JsonProperty("transactionUniqueNo") String transactionUniqueNo,
                @JsonProperty("categoryId") String categoryId,
                @JsonProperty("categoryName") String categoryName,
                @JsonProperty("merchantId") String merchantId,
                @JsonProperty("merchantName") String merchantName,
                @JsonProperty("transactionDate") String transactionDate,
                @JsonProperty("transactionTime") String transactionTime,
                @JsonProperty("paymentBalance") String paymentBalance
        ) {
        }
    }
}
