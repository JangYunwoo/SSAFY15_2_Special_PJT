package com.gongguhan.domain.account.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gongguhan.domain.payment.card.service.SsafyProperties;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * SSAFY 교육용 금융망의 카드 발급·조회 API를 감싼다(마이페이지 카드 온디맨드 발급).
 *
 * <p>발급 체인(실측 검증 2026-09-15):
 * <ol>
 *   <li>{@code inquireCreditCardList} - 발급 가능한 카드 상품의 {@code cardUniqueNo}</li>
 *   <li>{@code createCreditCard} - 출금계좌를 물려 카드 발급 → cardNo·cvc 즉시 반환</li>
 *   <li>{@code inquireSignUpCreditCardList} - 회원의 발급 카드 목록(cardNo·cvc 재조회 가능)</li>
 * </ol>
 *
 * <p>{@link SsafyAccountClient}와 같은 관례(concrete 빈, Header 블록 + userKey). 카드번호·CVC를
 * 우리 DB에 저장하지 않고 이 API로 온디맨드 조회하므로, 카드 정보는 SSAFY가 유일한 출처다.
 */
@Component
public class SsafyCardIssueClient {

    private static final Logger log = LoggerFactory.getLogger(SsafyCardIssueClient.class);
    private static final String INSTITUTION_CODE = "00100";
    private static final String FINTECH_APP_NO = "001";
    private static final String SUCCESS = "H0000";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmmss");

    private final RestClient restClient;
    private final SsafyProperties properties;

    public SsafyCardIssueClient(SsafyProperties properties) {
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

    /** 발급 가능한 첫 카드 상품의 cardUniqueNo. */
    public String firstCardProduct(String userKey) {
        ListResponse response = post("inquireCreditCardList", "/creditCard/inquireCreditCardList",
                Map.of(), userKey, ListResponse.class);
        if (response.rec() == null || response.rec().isEmpty()) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "SSAFY 카드 상품을 찾을 수 없습니다.");
        }
        return response.rec().get(0).cardUniqueNo();
    }

    /** 출금계좌를 물려 카드를 발급하고 발급 카드 정보를 반환한다. */
    public Card create(String userKey, String cardUniqueNo, String withdrawalAccountNo) {
        CardResponse response = post("createCreditCard", "/creditCard/createCreditCard",
                Map.of("cardUniqueNo", cardUniqueNo,
                        "withdrawalAccountNo", withdrawalAccountNo,
                        "withdrawalDate", "1"),
                userKey, CardResponse.class);
        if (response.rec() == null) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "SSAFY 카드 발급에 실패했습니다.");
        }
        return response.rec();
    }

    /** 회원의 발급 카드 목록. 없으면 빈 목록. */
    public List<Card> inquireSignedUp(String userKey) {
        ListResponse response = post("inquireSignUpCreditCardList", "/creditCard/inquireSignUpCreditCardList",
                Map.of(), userKey, ListResponse.class);
        return response.rec() == null ? List.of() : response.rec().stream().map(Product::toCard).toList();
    }

    private <T extends HasHeader> T post(String apiName, String path, Map<String, Object> extra,
                                          String userKey, Class<T> type) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("Header", header(apiName, userKey));
        body.putAll(extra);
        try {
            T response = restClient.post().uri(path).body(body).retrieve().body(type);
            if (response == null || response.header() == null) {
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "SSAFY 응답이 비어 있습니다.");
            }
            if (!SUCCESS.equals(response.header().responseCode())) {
                log.warn("SSAFY {} 실패 code={} msg={}", apiName,
                        response.header().responseCode(), response.header().responseMessage());
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                        "SSAFY 금융망 연동에 실패했습니다: " + response.header().responseMessage());
            }
            return response;
        } catch (RestClientException e) {
            log.error("SSAFY {} 호출 실패", apiName, e);
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "SSAFY 금융망 연동이 일시적으로 불가능합니다.");
        }
    }

    private Map<String, Object> header(String apiName, String userKey) {
        LocalDateTime now = LocalDateTime.now();
        String date = now.format(DATE);
        String time = now.format(TIME);
        String serial = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("apiName", apiName);
        header.put("transmissionDate", date);
        header.put("transmissionTime", time);
        header.put("institutionCode", INSTITUTION_CODE);
        header.put("fintechAppNo", FINTECH_APP_NO);
        header.put("apiServiceCode", apiName);
        header.put("institutionTransactionUniqueNo", date + time + serial);
        header.put("apiKey", properties.apiKey());
        header.put("userKey", userKey);
        return header;
    }

    interface HasHeader {
        Header header();
    }

    record Header(
            @JsonProperty("responseCode") String responseCode,
            @JsonProperty("responseMessage") String responseMessage) {
    }

    /** 발급된 카드. cardNo·cvc는 SSAFY만 보유하며 우리 DB엔 저장하지 않는다. */
    public record Card(String cardNo, String cvc, String cardName, String cardIssuerName,
                       String cardExpiryDate, String withdrawalAccountNo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CardResponse(@JsonProperty("Header") Header header, @JsonProperty("REC") Card rec)
            implements HasHeader {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ListResponse(@JsonProperty("Header") Header header, @JsonProperty("REC") List<Product> rec)
            implements HasHeader {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Product(
            @JsonProperty("cardUniqueNo") String cardUniqueNo,
            @JsonProperty("cardNo") String cardNo,
            @JsonProperty("cvc") String cvc,
            @JsonProperty("cardName") String cardName,
            @JsonProperty("cardIssuerName") String cardIssuerName,
            @JsonProperty("cardExpiryDate") String cardExpiryDate,
            @JsonProperty("withdrawalAccountNo") String withdrawalAccountNo) {

        Card toCard() {
            return new Card(cardNo, cvc, cardName, cardIssuerName, cardExpiryDate, withdrawalAccountNo);
        }
    }
}
