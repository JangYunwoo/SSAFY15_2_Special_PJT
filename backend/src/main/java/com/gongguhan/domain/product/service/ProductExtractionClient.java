package com.gongguhan.domain.product.service;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI 상품정보 추출 모듈 클라이언트.
 *
 * <p>판매자가 등록 화면에서 올린 이미지 <b>전체</b>를 AI 모듈로 보내 상품 필드를 추출한다.
 * 추출은 상품이 저장되기 <b>전</b>(온디맨드) 단계라 이미지가 아직 어디에도 영속화돼 있지
 * 않으므로, 업로드된 바이트를 그대로 전송한다. 이 계약은 스토리지(S3/로컬)와 무관하다.
 *
 * <p><b>S3 도입 후에도 그대로 동작한다.</b> S3 는 상품 <i>저장</i> 경로(ProductImageStorage)에만
 * 쓰이고 추출 경로는 건드리지 않는다. 만약 나중에 "이미 S3 에 올린 이미지의 URL 로 분석"하는
 * 방식으로 바꾸려면, 이 인터페이스의 새 구현(URL 전송)으로 교체하면 되고
 * {@link AiExtractionService} 는 손대지 않아도 된다.
 */
public interface ProductExtractionClient {

    AiModuleExtraction extract(List<MultipartFile> images);
}
