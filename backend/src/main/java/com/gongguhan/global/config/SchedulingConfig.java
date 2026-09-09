package com.gongguhan.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 스케줄러 활성화.
 *
 * <p><b>중복 실행 주의.</b> {@code @Scheduled}는 인스턴스마다 독립적으로 돈다.
 * 지금은 EC2 한 대이므로 문제가 없지만, 인스턴스를 늘리는 순간 같은 배치가 동시에
 * 실행된다. 코인 컨펌 폴링은 결제 확정을 일으키므로 중복 실행이 곧 참여 인원 초과로
 * 이어질 수 있다.
 *
 * <p>인스턴스를 2대 이상으로 늘릴 때는 ShedLock 같은 분산 락을 도입해야 한다.
 * 지금 넣지 않은 이유는 공용 {@code build.gradle}에 의존성을 하나 더 추가해야 하고
 * 단일 인스턴스에서는 이득이 없기 때문이다.
 *
 * <p><b>전용 스레드 풀을 두는 이유.</b> 기본 스케줄러는 단일 스레드다. 코인 폴링이
 * 체인 RPC 를 기다리는 동안 다른 배치(슬롯 만료, 정산 등)가 전부 밀린다. 배치가
 * 늘어날 것을 알고 있으므로 처음부터 풀로 둔다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("batch-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        // 종료 시 진행 중인 배치를 기다린다. 결제 확정 중간에 끊기면
        // 자금은 들어왔는데 참여 건이 없는 상태로 남을 수 있다.
        scheduler.setAwaitTerminationSeconds(20);
        return scheduler;
    }
}
