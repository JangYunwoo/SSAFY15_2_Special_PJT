package com.gongguhan.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 슬롯 만료, 모집 종료 판정, 자동 구매확정, 반품 자동 승인/환불, 정산 배치 등
 * API 명세서 18장의 스케줄 작업이 여기서 활성화된 @Scheduled를 사용한다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
