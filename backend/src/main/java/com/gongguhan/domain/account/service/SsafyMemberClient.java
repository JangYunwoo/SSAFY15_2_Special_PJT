package com.gongguhan.domain.account.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gongguhan.domain.payment.card.service.SsafyProperties;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * SSAFY 교육용 금융망 member 생성·조회 (S15P21B205-63).
 *
 * <p>{@link com.gongguhan.domain.payment.card.service.SsafyCardApiClient}와 같은 관례
 * (concrete 빈, 테스트는 Mockito mock). 단 member API는 카드 API와 달리 Header 블록
 * 없이 {@code apiKey}·{@code userId}만 보내고, 실패 시 HTTP 4xx +
 * {@code responseCode}(E4002: 이미 존재, E4003: 없음) 바디로 응답한다 - 그래서
 * 4xx도 예외가 아니라 데이터로 돌려주고 판정은 호출자 몫이다.
 *
 * <p>설정은 카드 연동과 같은 {@code app.ssafy.*}를 쓰므로 {@link SsafyProperties}를
 * 재사용한다(도메인 로직이 아닌 설정 레코드라 경계 위반으로 보지 않는다).
 */
@Component
public class SsafyMemberClient {

    private static final Logger log = LoggerFactory.getLogger(SsafyMemberClient.class);
    public static final String RESPONSE_CODE_ALREADY_EXISTS = "E4002";
    public static final String RESPONSE_CODE_NOT_FOUND = "E4003";

    private final RestClient restClient;
    private final SsafyProperties properties;

    public SsafyMemberClient(SsafyProperties properties) {
        Duration timeout = Duration.ofMillis(properties.timeoutMs());
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        requestFactory.setReadTimeout(timeout);

        this.restClient = RestClient.builder()
                .baseUrl(memberBaseUrl(properties.baseUrl()))
                .requestFactory(requestFactory)
                .build();
        this.properties = properties;
    }

    /**
     * member API는 상품·거래 API와 달리 {@code /edu} 프리픽스 밖에 산다
     * (실호출로 확인: {@code .../v1/edu/member/}는 게이트웨이가 {@code code=failed}만
     * 돌려주고, {@code .../v1/member/}가 정상 응답한다). 설정은 카드 연동과 공유하므로
     * 여기서 프리픽스만 걷어낸다.
     */
    static String memberBaseUrl(String baseUrl) {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalized.endsWith("/edu")
                ? normalized.substring(0, normalized.length() - "/edu".length())
                : normalized;
    }

    /** member 생성. 이미 존재하는 이메일이면 {@code E4002}가 담긴 응답이 돌아온다. */
    public SsafyMemberResponse createMember(String email) {
        return call("/member/", email);
    }

    /** 기존 member의 userKey 조회. */
    public SsafyMemberResponse searchMember(String email) {
        return call("/member/search", email);
    }

    private SsafyMemberResponse call(String path, String email) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("apiKey", properties.apiKey());
        body.put("userId", email);

        try {
            return restClient.post()
                    .uri(path)
                    .body(body)
                    .exchange((request, response) -> {
                        SsafyMemberResponse parsed = response.bodyTo(SsafyMemberResponse.class);
                        if (parsed == null) {
                            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                                    "SSAFY 금융망 응답이 비어 있습니다.");
                        }
                        return parsed;
                    });
        } catch (RestClientException e) {
            log.warn("SSAFY member 호출 실패 path={}", path, e);
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "SSAFY 금융망 연동에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    /** 성공 시 userKey, 실패 시 responseCode/responseMessage만 채워진다. */
    public record SsafyMemberResponse(
            @JsonProperty("userKey") String userKey,
            @JsonProperty("responseCode") String responseCode,
            @JsonProperty("responseMessage") String responseMessage
    ) {

        public boolean hasUserKey() {
            return userKey != null && !userKey.isBlank();
        }

        public boolean isAlreadyExists() {
            return RESPONSE_CODE_ALREADY_EXISTS.equals(responseCode);
        }

        /** 우리 apiKey 기준으로 해당 회원이 없음(E4003). 조회 실패 또는 다른 apiKey 소유. */
        public boolean isNotFound() {
            return RESPONSE_CODE_NOT_FOUND.equals(responseCode);
        }
    }
}
