package com.gongguhan.domain.payment.card.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.payment.card.entity.SsafyApiLog;
import com.gongguhan.domain.payment.card.repository.SsafyApiLogRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SsafyApiLogServiceTest {

    private static final String API_SERVICE_CODE = "createCreditCardTransaction";

    private SsafyApiLogRepository ssafyApiLogRepository;
    private SsafyApiLogService ssafyApiLogService;

    @BeforeEach
    void setUp() {
        ssafyApiLogRepository = mock(SsafyApiLogRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-08T13:56:00Z"), ZoneOffset.UTC);
        ssafyApiLogService = new SsafyApiLogService(ssafyApiLogRepository, new ObjectMapper(), clock);
    }

    @Test
    void generatesTwentyCharacterNumberWithDateTimePrefix() {
        when(ssafyApiLogRepository.existsByApiServiceCodeAndInstitutionTxNo(eq(API_SERVICE_CODE), anyString()))
                .thenReturn(false);

        String institutionTxNo = ssafyApiLogService.generateInstitutionTransactionUniqueNo(API_SERVICE_CODE);

        assertEquals(20, institutionTxNo.length());
        assertTrue(institutionTxNo.startsWith("20260408135600"));
    }

    @Test
    void retriesUntilAnUnusedCandidateIsFound() {
        when(ssafyApiLogRepository.existsByApiServiceCodeAndInstitutionTxNo(eq(API_SERVICE_CODE), anyString()))
                .thenReturn(true, true, false);

        String institutionTxNo = ssafyApiLogService.generateInstitutionTransactionUniqueNo(API_SERVICE_CODE);

        assertEquals(20, institutionTxNo.length());
        verify(ssafyApiLogRepository, times(3))
                .existsByApiServiceCodeAndInstitutionTxNo(eq(API_SERVICE_CODE), anyString());
    }

    @Test
    void givesUpAfterMaxAttempts() {
        when(ssafyApiLogRepository.existsByApiServiceCodeAndInstitutionTxNo(eq(API_SERVICE_CODE), anyString()))
                .thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> ssafyApiLogService.generateInstitutionTransactionUniqueNo(API_SERVICE_CODE));

        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, exception.getErrorCode());
        verify(ssafyApiLogRepository, times(5))
                .existsByApiServiceCodeAndInstitutionTxNo(eq(API_SERVICE_CODE), anyString());
    }

    @Test
    void recordSavesLogRegardlessOfOutcome() {
        ssafyApiLogService.record(API_SERVICE_CODE, "20260408135600123456", "PAYMENT", 200L,
                new Object(), "H0000", "정상처리 되었습니다.", new Object());

        verify(ssafyApiLogRepository, times(1)).save(any(SsafyApiLog.class));
    }
}
