package com.gongguhan.domain.product.service;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
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
    public AiModuleExtraction extract(List<MultipartFile> images) {
        // 같은 파트명("images")을 반복해 담으면 FastAPI 는 List[UploadFile] 로 수신한다.
        // 순서는 유지되며, 첫 장이 대표 이미지다.
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        for (MultipartFile image : images) {
            parts.add("images", image.getResource());
        }
        return restClient.post()
                .uri(extractionPath)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve()
                .body(AiModuleExtraction.class);
    }
}
