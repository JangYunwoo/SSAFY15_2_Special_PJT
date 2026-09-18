package com.gongguhan.domain.product.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class FastApiProductExtractionClient implements ProductExtractionClient {

    private final RestClient restClient;
    private final String extractionPath;

    public FastApiProductExtractionClient(AiExtractionProperties properties) {
        Duration timeout = Duration.ofMillis(properties.timeoutMs());
        // AI(uvicorn)는 HTTP/1.1 전용이다. JDK HttpClient 기본값(HTTP/2)은 cleartext h2c 업그레이드를
        // 시도하는데 uvicorn 이 이를 거부하면서 요청 본문 파싱이 깨져 422 가 난다. HTTP/1.1 로 고정한다.
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(timeout)
                        .build());
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
        this.extractionPath = properties.path();
    }

    @Override
    public AiModuleExtraction extract(List<String> imageUrls) {
        return restClient.post()
                .uri(extractionPath)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ImageUrlRequest(imageUrls))
                .retrieve()
                .body(AiModuleExtraction.class);
    }

    private record ImageUrlRequest(@JsonProperty("image_urls") List<String> imageUrls) {
    }
}
