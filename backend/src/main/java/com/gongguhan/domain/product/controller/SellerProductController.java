package com.gongguhan.domain.product.controller;

import com.gongguhan.domain.product.dto.ProductResponse;
import com.gongguhan.domain.product.dto.ProductUpsertRequest;
import com.gongguhan.domain.product.service.ProductService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.PageResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/sellers/products")
public class SellerProductController {

    private final ProductService productService;

    public SellerProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProductResponse>> create(
            @AuthenticationPrincipal Long sellerId,
            @Valid @RequestPart("product") ProductUpsertRequest request,
            @RequestPart("images") List<MultipartFile> images,
            HttpServletRequest httpRequest) {
        ProductResponse response = productService.create(sellerId, request, images);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, TraceIdUtils.from(httpRequest)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getSellerProducts(
            @AuthenticationPrincipal Long sellerId,
            @PageableDefault(size = 20, sort = "productId") Pageable pageable,
            HttpServletRequest httpRequest) {
        Page<ProductResponse> page = productService.getSellerProducts(sellerId, pageable);
        return ResponseEntity.ok(ApiResponse.success(
                page.getContent(), PageResponse.from(page), TraceIdUtils.from(httpRequest)));
    }

    @PutMapping(path = "/{productId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProductResponse>> update(
            @AuthenticationPrincipal Long sellerId,
            @PathVariable Long productId,
            @Valid @RequestPart("product") ProductUpsertRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            HttpServletRequest httpRequest) {
        ProductResponse response = productService.update(sellerId, productId, request, images);
        return ResponseEntity.ok(ApiResponse.success(response, TraceIdUtils.from(httpRequest)));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal Long sellerId,
            @PathVariable Long productId,
            HttpServletRequest httpRequest) {
        productService.delete(sellerId, productId);
        return ResponseEntity.ok(ApiResponse.success(null, TraceIdUtils.from(httpRequest)));
    }
}
