package com.gongguhan.global.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 슬롯 만료 등 시각 비교 로직을 테스트에서 고정할 수 있도록 Clock을 빈으로 분리한다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
