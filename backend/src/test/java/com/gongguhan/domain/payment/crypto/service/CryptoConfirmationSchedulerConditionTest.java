package com.gongguhan.domain.payment.crypto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.gongguhan.domain.payment.crypto.repository.CryptoPaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 컨펌 폴링을 끄는 방법이 실제로 동작하는지 검증.
 *
 * <p><b>왜 필요한가.</b> 원래 {@code application.yml} 주석은 "주기를 0 이하로 두면
 * 스케줄러가 뜨지 않는다" 고 설명했지만 사실이 아니었다.
 * {@code @Scheduled} 의 {@code fixedDelay} 는 양수여야 해서, 0 을 넣으면 폴링이
 * 조용히 멈추는 대신 <b>애플리케이션 기동이 실패</b>한다. 그 주석을 믿고 폴링을
 * 끄려던 사람은 앱을 띄울 수 없다.
 *
 * <p>그래서 {@code @ConditionalOnProperty} 로 빈 생성 자체를 가르는 방식으로 바꿨고,
 * 이 테스트가 그 스위치를 고정한다. 전역 스위치
 * {@code app.scheduling.enabled} 와 같은 규약이다.
 */
class CryptoConfirmationSchedulerConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(StubDependencies.class, CryptoConfirmationScheduler.class);

    @Test
    @DisplayName("기본값에서는 폴링 스케줄러가 등록된다")
    void registeredByDefault() {
        contextRunner.run(context ->
                assertThat(context).hasSingleBean(CryptoConfirmationScheduler.class));
    }

    @Test
    @DisplayName("confirmation-poll-enabled=false 면 빈 자체가 만들어지지 않는다")
    void notRegisteredWhenDisabled() {
        contextRunner
                .withPropertyValues("app.crypto.confirmation-poll-enabled=false")
                .run(context ->
                        assertThat(context).doesNotHaveBean(CryptoConfirmationScheduler.class));
    }

    /**
     * 스케줄러는 협력 객체 네 개를 생성자로 받는다. 조건 판정만 확인하면 되므로
     * 동작하지 않는 mock 으로 채운다.
     */
    @Configuration(proxyBeanMethods = false)
    static class StubDependencies {

        @Bean
        CryptoPaymentRepository cryptoPaymentRepository() {
            return mock(CryptoPaymentRepository.class);
        }

        @Bean
        CryptoTxRecordService cryptoTxRecordService() {
            return mock(CryptoTxRecordService.class);
        }

        @Bean
        ChainClient chainClient() {
            return mock(ChainClient.class);
        }

        @Bean
        CryptoProperties cryptoProperties() {
            return mock(CryptoProperties.class);
        }
    }
}
