package com.gongguhan.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class SchedulingConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(SchedulingConfig.class);

    @Test
    void enablesSchedulerByDefault() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(SchedulingConfig.class)
                .hasSingleBean(ThreadPoolTaskScheduler.class));
    }

    @Test
    void disablesSchedulerWhenConfiguredForTests() {
        contextRunner
                .withPropertyValues("app.scheduling.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(SchedulingConfig.class)
                        .doesNotHaveBean(ThreadPoolTaskScheduler.class));
    }
}
