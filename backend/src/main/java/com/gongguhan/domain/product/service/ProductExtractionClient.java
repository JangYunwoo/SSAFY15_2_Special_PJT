package com.gongguhan.domain.product.service;

import java.util.List;

/**
 * AI 상품정보 추출 모듈 클라이언트.
 *
 * <p>AI 서버와 Gemini가 직접 내려받을 수 있는 HTTPS 이미지 URL을 순서대로 전달한다.
 * 분석용 임시 업로드와 삭제는 {@link AiExtractionService}가 담당하고, 이 포트는 외부 API
 * 계약만 표현한다.
 */
public interface ProductExtractionClient {

    AiModuleExtraction extract(List<String> imageUrls);
}
