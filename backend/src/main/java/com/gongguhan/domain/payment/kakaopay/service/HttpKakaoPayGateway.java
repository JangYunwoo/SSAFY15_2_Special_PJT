package com.gongguhan.domain.payment.kakaopay.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** 카카오페이 온라인 결제 REST API 어댑터. */
@Component
public class HttpKakaoPayGateway implements KakaoPayGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpKakaoPayGateway.class);
    private static final String READY_PATH = "/online/v1/payment/ready";
    private static final String APPROVE_PATH = "/online/v1/payment/approve";

    private final RestClient restClient;
    private final KakaoPayProperties properties;

    public HttpKakaoPayGateway(KakaoPayProperties properties) {
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

    @Override
    public ReadyResult ready(ReadyCommand command) {
        ensureConfigured();
        ReadyApiResponse response = post(READY_PATH, new ReadyApiRequest(
                properties.cid(), command.partnerOrderId(), command.partnerUserId(),
                command.itemName(), 1, command.totalAmount(), 0,
                command.approvalUrl(), command.cancelUrl(), command.failUrl()),
                ReadyApiResponse.class);
        if (response == null || blank(response.tid()) || blank(response.nextRedirectPcUrl())) {
            throw invalidResponse("ready");
        }
        return new ReadyResult(response.tid(), response.nextRedirectPcUrl());
    }

    @Override
    public ApproveResult approve(ApproveCommand command) {
        ensureConfigured();
        ApproveApiResponse response = post(APPROVE_PATH, new ApproveApiRequest(
                properties.cid(), command.tid(), command.partnerOrderId(),
                command.partnerUserId(), command.pgToken()), ApproveApiResponse.class);
        if (response == null || blank(response.aid()) || blank(response.tid())
                || response.amount() == null) {
            throw invalidResponse("approve");
        }
        return new ApproveResult(
                response.aid(), response.tid(), response.partnerOrderId(),
                response.partnerUserId(), response.amount().total(), response.amount().vat(),
                response.paymentMethodType());
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        try {
            return restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", properties.authorizationValue())
                    .body(body)
                    .retrieve()
                    .body(responseType);
        } catch (RestClientResponseException e) {
            // 응답 본문에는 결제기관 내부 메시지가 포함될 수 있어 로그에 남기지 않는다.
            log.warn("카카오페이 API 거절 path={} status={}", path, e.getStatusCode().value());
            throw new BusinessException(ErrorCode.EXTERNAL_PAYMENT_ERROR,
                    "카카오페이에서 결제 요청을 처리하지 못했습니다.");
        } catch (RestClientException e) {
            log.warn("카카오페이 API 연결 실패 path={}", path, e);
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "카카오페이에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private void ensureConfigured() {
        if (!properties.hasSecretKey()) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "카카오페이 개발용 비밀키가 설정되지 않았습니다.");
        }
    }

    private BusinessException invalidResponse(String operation) {
        log.warn("카카오페이 {} 응답의 필수 필드가 비어 있음", operation);
        return new BusinessException(ErrorCode.EXTERNAL_PAYMENT_ERROR,
                "카카오페이 응답을 검증할 수 없습니다.");
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record ReadyApiRequest(
            String cid,
            @JsonProperty("partner_order_id") String partnerOrderId,
            @JsonProperty("partner_user_id") String partnerUserId,
            @JsonProperty("item_name") String itemName,
            int quantity,
            @JsonProperty("total_amount") long totalAmount,
            @JsonProperty("tax_free_amount") long taxFreeAmount,
            @JsonProperty("approval_url") String approvalUrl,
            @JsonProperty("cancel_url") String cancelUrl,
            @JsonProperty("fail_url") String failUrl
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ReadyApiResponse(
            String tid,
            @JsonProperty("next_redirect_pc_url") String nextRedirectPcUrl
    ) {
    }

    private record ApproveApiRequest(
            String cid,
            String tid,
            @JsonProperty("partner_order_id") String partnerOrderId,
            @JsonProperty("partner_user_id") String partnerUserId,
            @JsonProperty("pg_token") String pgToken
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApproveApiResponse(
            String aid,
            String tid,
            @JsonProperty("partner_order_id") String partnerOrderId,
            @JsonProperty("partner_user_id") String partnerUserId,
            @JsonProperty("payment_method_type") String paymentMethodType,
            Amount amount
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Amount(long total, @JsonProperty("tax_free") long taxFree, long vat) {
    }
}
