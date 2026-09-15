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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * SSAFY 교육용 금융망의 계좌 개설·1원 인증 API를 감싼다(판매자 전환 SEL-03·04).
 *
 * <p>정산계좌 1원 인증 체인(실측 검증 2026-09-15):
 * <ol>
 *   <li>{@code inquireDemandDepositList} - 수시입출금 상품의 {@code accountTypeUniqueNo}</li>
 *   <li>{@code createDemandDepositAccount} - 계좌가 없으면 개설 → {@code accountNo}</li>
 *   <li>{@code openAccountAuth} - 대상 계좌로 1원 송금(적요에 "SSAFY " + 난수 4자리)</li>
 *   <li>{@code inquireTransactionHistoryList} - 그 적요에서 인증코드 4자리를 읽는다(데모 편의)</li>
 *   <li>{@code checkAuthCode} - 코드 검증</li>
 * </ol>
 *
 * <p>{@link com.gongguhan.domain.refund.service.SsafyTransferClient}와 같은 관례를 따른다
 * (concrete 빈, Header 블록 + userKey). 헤더 구성이 중복되지만 소유 도메인이 달라 공용
 * 추출은 별도 합의 후로 미룬다(그 파일의 주석과 동일한 판단).
 */
@Component
public class SsafyAccountClient {

    private static final Logger log = LoggerFactory.getLogger(SsafyAccountClient.class);

    private static final String INSTITUTION_CODE = "00100";
    private static final String FINTECH_APP_NO = "001";
    private static final String SUCCESS = "H0000";
    /** 1원 입금 적요 접두어. 검증 때도 같은 값을 보내야 한다. */
    public static final String AUTH_TEXT = "SSAFY";
    private static final Pattern CODE_PATTERN = Pattern.compile("(\\d{4})\\s*$");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmmss");

    private final RestClient restClient;
    private final SsafyProperties properties;

    public SsafyAccountClient(SsafyProperties properties) {
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

    /** 수시입출금 계좌를 개설하고 계좌번호를 돌려준다. */
    public String createAccount(String userKey) {
        ListResponse products = post("inquireDemandDepositList", "/demandDeposit/inquireDemandDepositList",
                Map.of(), userKey, ListResponse.class);
        if (products.rec() == null || products.rec().isEmpty()) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "SSAFY 예금 상품을 찾을 수 없습니다.");
        }
        String productCode = products.rec().get(0).accountTypeUniqueNo();

        AccountResponse account = post("createDemandDepositAccount", "/demandDeposit/createDemandDepositAccount",
                Map.of("accountTypeUniqueNo", productCode), userKey, AccountResponse.class);
        if (account.rec() == null || account.rec().accountNo() == null) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "SSAFY 계좌 개설에 실패했습니다.");
        }
        return account.rec().accountNo();
    }

    /**
     * 대상 계좌로 1원을 송금하고, 입금 적요에서 인증코드 4자리를 읽어 돌려준다.
     *
     * <p>실제 은행앱 없이 데모하기 위해 거래내역 조회로 코드를 자동 회수한다.
     */
    public String sendOneWonAuth(String userKey, String accountNo) {
        post("openAccountAuth", "/accountAuth/openAccountAuth",
                Map.of("accountNo", accountNo, "authText", AUTH_TEXT), userKey, HeaderOnlyResponse.class);
        return readAuthCode(userKey, accountNo);
    }

    /** 1원 인증코드를 검증한다. */
    public boolean verifyOneWonAuth(String userKey, String accountNo, String authCode) {
        HeaderOnlyResponse response = post("checkAuthCode", "/accountAuth/checkAuthCode",
                Map.of("accountNo", accountNo, "authText", AUTH_TEXT, "authCode", authCode),
                userKey, HeaderOnlyResponse.class);
        return response.header() != null && SUCCESS.equals(response.header().responseCode());
    }

    private String readAuthCode(String userKey, String accountNo) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountNo", accountNo);
        body.put("startDate", "20200101");
        body.put("endDate", "20301231");
        body.put("transactionType", "A");
        body.put("orderByType", "DESC");
        HistoryResponse history = post("inquireTransactionHistoryList",
                "/demandDeposit/inquireTransactionHistoryList", body, userKey, HistoryResponse.class);
        List<History> list = history.rec() == null ? List.of() : history.rec().list();
        for (History tx : list == null ? List.<History>of() : list) {
            String summary = tx.transactionSummary();
            if (summary != null && summary.startsWith(AUTH_TEXT)) {
                Matcher matcher = CODE_PATTERN.matcher(summary);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        }
        throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "1원 인증코드를 확인하지 못했습니다.");
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HeaderOnlyResponse(@JsonProperty("Header") Header header) implements HasHeader {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ListResponse(@JsonProperty("Header") Header header,
                        @JsonProperty("REC") List<Product> rec) implements HasHeader {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Product(@JsonProperty("accountTypeUniqueNo") String accountTypeUniqueNo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AccountResponse(@JsonProperty("Header") Header header,
                           @JsonProperty("REC") Account rec) implements HasHeader {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Account(@JsonProperty("accountNo") String accountNo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HistoryResponse(@JsonProperty("Header") Header header,
                           @JsonProperty("REC") HistoryList rec) implements HasHeader {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HistoryList(@JsonProperty("list") List<History> list) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record History(@JsonProperty("transactionSummary") String transactionSummary) {
    }
}
