/**
 * 상품·카테고리·AI 추출 도메인.
 *
 * <p>담당: 카카오페이결제 담당자
 * <p>관련 API: CAT-01, PRD-01~05, AI-01, WDR-01~03 (공구한_API_명세서_v1.md 3장)
 * <p>관련 테이블: categories, products, product_images, withdrawal_restriction_reasons
 *
 * <p>AI 분석은 동기 API로만 처리하고 중간 결과를 DB에 저장하지 않는다. 판매자가 결과를
 * 검수해 상품을 등록할 때만 {@code products.ai_extracted}를 기록한다(API 명세서 3장).
 */
package com.gongguhan.domain.product;
