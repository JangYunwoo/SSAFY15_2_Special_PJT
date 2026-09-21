package com.gongguhan.domain.payment.kakaopay;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gongguhan.domain.payment.common.service.PaymentService;
import com.gongguhan.domain.payment.kakaopay.controller.KakaoPayController;
import com.gongguhan.domain.payment.kakaopay.service.*;
import com.gongguhan.global.error.GlobalExceptionHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** 실제 컨트롤러·서명 검증과 MVC 요청 바인딩을 검사한다. 금융/DB 호출은 대체한다. */
class KakaoPayCallbackBoundaryTest {
    private MockMvc mvc;
    private PaymentService payments;
    private KakaoPayRecordService records;
    private KakaoPayStateSigner signer;
    private static final Instant NOW = Instant.parse("2026-09-21T00:00:00Z");

    @BeforeEach
    void setup() {
        payments = mock(PaymentService.class);
        records = mock(KakaoPayRecordService.class);
        var properties = new KakaoPayProperties("https://pay.example", "TEST", "fixture-secret",
                "SECRET_KEY", "http://localhost:5173", 1000);
        signer = new KakaoPayStateSigner(properties, Clock.fixed(NOW, ZoneOffset.UTC));
        var service = new KakaoPayService(payments, records, mock(KakaoPayGateway.class), signer, properties);
        mvc = MockMvcBuilders.standaloneSetup(new KakaoPayController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
                    }
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                            NativeWebRequest request, WebDataBinderFactory binderFactory) { return 100L; }
                }).build();
    }

    @ParameterizedTest @ValueSource(strings = {"cancel-flow", "fail-flow"})
    void missingBodyMustNotEndPayment(String flow) throws Exception {
        mvc.perform(post(path(flow))).andExpect(status().isBadRequest());
        verifyNoInteractions(payments, records);
    }

    @ParameterizedTest @ValueSource(strings = {"cancel-flow", "fail-flow"})
    void blankStateMustNotEndPayment(String flow) throws Exception {
        request(flow, "{\"state\":\" \"}", 400);
    }

    @ParameterizedTest @ValueSource(strings = {"cancel-flow", "fail-flow"})
    void malformedJsonMustNotEndPayment(String flow) throws Exception {
        request(flow, "{", 400);
    }

    @ParameterizedTest @ValueSource(strings = {"cancel-flow", "fail-flow"})
    void unsignedStateMustNotEndPayment(String flow) throws Exception {
        request(flow, "{\"state\":\"not-signed\"}", 400);
    }

    @ParameterizedTest @ValueSource(strings = {"cancel-flow", "fail-flow"})
    void anotherUserStateMustNotEndPayment(String flow) throws Exception {
        request(flow, body(9001L, 999L, NOW.plusSeconds(300)), 403);
    }

    @ParameterizedTest @ValueSource(strings = {"cancel-flow", "fail-flow"})
    void anotherPaymentStateMustNotEndPayment(String flow) throws Exception {
        request(flow, body(9002L, 100L, NOW.plusSeconds(300)), 403);
    }

    @ParameterizedTest @ValueSource(strings = {"cancel-flow", "fail-flow"})
    void expiredStateMustNotEndPayment(String flow) throws Exception {
        request(flow, body(9001L, 100L, NOW), 409);
    }

    private void request(String flow, String body, int expected) throws Exception {
        mvc.perform(post(path(flow)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected));
        verifyNoInteractions(payments, records);
    }

    @ParameterizedTest @ValueSource(strings = {"cancel-flow", "fail-flow"})
    void missingStateFieldIsRejected(String flow) throws Exception {
        request(flow, "{}", 400);
    }

    @ParameterizedTest @ValueSource(strings = {"cancel-flow", "fail-flow"})
    void excessiveStateIsRejected(String flow) throws Exception {
        request(flow, "{\"state\":\"" + "x".repeat(513) + "\"}", 400);
    }

    @ParameterizedTest @ValueSource(strings = {"cancel-flow", "fail-flow"})
    void validBodyReachesTransactionalRecordVerification(String flow) throws Exception {
        mvc.perform(post(path(flow)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(9001L, 100L, NOW.plusSeconds(300))))
                .andExpect(status().isOk());
        verify(records).endCallback(eq(100L), argThat(claims ->
                        claims.paymentId().equals(9001L) && claims.userId().equals(100L)
                                && claims.nonce().equals("nonce")),
                eq(flow.equals("cancel-flow") ? "KAKAOPAY_USER_CANCELLED" : "KAKAOPAY_AUTH_FAILED"));
        verifyNoInteractions(payments);
    }
    private String body(long paymentId, long userId, Instant expiresAt) {
        return "{\"state\":\"" + signer.sign(new KakaoPayStateSigner.StateClaims(
                paymentId, userId, expiresAt.getEpochSecond(), "nonce")) + "\"}";
    }
    private String path(String flow) { return "/payments/9001/kakaopay/" + flow; }
}
