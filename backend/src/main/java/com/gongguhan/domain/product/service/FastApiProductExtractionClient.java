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
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
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
