package com.gongguhan.domain.payment.card.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SSAFY 교육용 금융망 연동 설정.
 *
 * <p>기관코드·핀테크앱일련번호·가맹점ID는 {@link com.gongguhan.global.policy.PolicyKey}로
 * 관리한다(관리자가 재배포 없이 바꿀 수 있어야 하는 값). 여기는 비밀값·엔드포인트만 둔다.
 *
 * @param baseUrl   {@code createCreditCardTransaction} 등 SSAFY API의 기준 URL
 * @param apiKey    SSAFY 개발자센터에서 발급받는 앱 apiKey. 비어 있으면 모든 호출이
 *                  H1008(API_KEY 유효하지 않음)로 거부된다.
 * @param timeoutMs 연결·응답 타임아웃. 결제 화면을 무한정 붙잡지 않도록 명시한다.
 */
@ConfigurationProperties(prefix = "app.ssafy")
public record SsafyProperties(
        String baseUrl,
        String apiKey,
        int timeoutMs
) {
}
