package com.gongguhan.global.error;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gongguhan.domain.refund.controller.AdminRefundController;
import com.gongguhan.domain.refund.service.RefundOrchestrationService;
import com.gongguhan.domain.refund.service.RefundQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 실제 MVC 라우팅/예외 처리, 금융 업무 경계는 모킹. 인증 정책은 B20 통합 시험이 담당한다. */
class HttpMethodBoundaryTest {
    private MockMvc mvc;
    private RefundQueryService query;
    private RefundOrchestrationService refunds;

    @BeforeEach
    void setUp() {
        query = mock(RefundQueryService.class);
        refunds = mock(RefundOrchestrationService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AdminRefundController(query, refunds))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PUT", "PATCH", "DELETE"})
    void wrongMethodCannotRunFinancialRetry(String method) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), "/admin/refunds/17/retry")
                        .header("Idempotency-Key", "qa-method-boundary"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
        verifyNoInteractions(query, refunds);
    }

    @Test
    void correctPostStillReachesTheRetryBusinessBoundary() throws Exception {
        when(refunds.retry(null, 17L)).thenThrow(new BusinessException(ErrorCode.INVALID_STATE_TRANSITION));
        mvc.perform(post("/admin/refunds/17/retry").header("Idempotency-Key", "qa-method-boundary"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"));
        verify(refunds).retry(null, 17L);
        verifyNoInteractions(query);
    }

    @Test
    void existingGetDetailStillUsesItsReadOnlyRoute() throws Exception {
        when(query.getRefund(17L)).thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        mvc.perform(get("/admin/refunds/17"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        verify(query).getRefund(17L);
        verifyNoInteractions(refunds);
    }
}
