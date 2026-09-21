package com.gongguhan.domain.recommendation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gongguhan.domain.recommendation.service.RecommendationService;
import com.gongguhan.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.bind.support.WebDataBinderFactory;

class RecommendationEventValidationTest {
    private RecommendationService service;
    private MockMvc mvc;

    @BeforeEach void setup() {
        service = mock(RecommendationService.class);
        mvc = MockMvcBuilders.standaloneSetup(new RecommendationController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
                    }
                    @Override public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                            NativeWebRequest request, WebDataBinderFactory binder) { return 1L; }
                }).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"[null]", "[{\"groupBuyId\":1,\"eventType\":\"CLICK\"},null]"})
    void nullItemsAreRejectedBeforeService(String events) throws Exception {
        mvc.perform(post("/recommendations/events").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"00000000-0000-0000-0000-000000000001\",\"events\":" + events + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @Test void validEventStillReachesService() throws Exception {
        mvc.perform(post("/recommendations/events").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":"00000000-0000-0000-0000-000000000001",
                                 "events":[{"groupBuyId":1,"eventType":"CLICK"}]}
                                """))
                .andExpect(status().isOk());
        verify(service).recordEvents(eq(1L), any());
    }
}
