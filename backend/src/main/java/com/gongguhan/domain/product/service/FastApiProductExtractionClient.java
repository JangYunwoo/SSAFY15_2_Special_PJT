package com.gongguhan.domain.product.service;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

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
    public AiModuleExtraction extract(MultipartFile representativeImage) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        Resource imageResource = representativeImage.getResource();
        parts.add("images", imageResource);
        return restClient.post()
                .uri(extractionPath)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve()
                .body(AiModuleExtraction.class);
    }
}
