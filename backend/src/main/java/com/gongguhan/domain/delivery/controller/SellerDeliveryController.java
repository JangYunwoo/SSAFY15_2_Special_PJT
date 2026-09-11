package com.gongguhan.domain.delivery.controller;

import com.gongguhan.domain.delivery.dto.DeliveryBulkStatusResponse;
import com.gongguhan.domain.delivery.dto.DeliveryInvoiceRequest;
import com.gongguhan.domain.delivery.dto.DeliveryResponse;
import com.gongguhan.domain.delivery.dto.DeliveryStatusUpdateRequest;
import com.gongguhan.domain.delivery.dto.SellerDeliveryListResponse;
import com.gongguhan.domain.delivery.service.DeliveryService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 판매자 배송 API (DLV-01~04). /sellers/**는 SecurityConfig에서 SELLER 역할을 요구한다. */
@RestController
@RequestMapping("/sellers")
public class SellerDeliveryController {

    private final DeliveryService deliveryService;

    public SellerDeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @GetMapping("/group-buys/{groupBuyId}/deliveries")
    public ResponseEntity<ApiResponse<SellerDeliveryListResponse>> getDeliveries(
            @AuthenticationPrincipal Long sellerId,
            @PathVariable Long groupBuyId,
            HttpServletRequest httpRequest) {
        SellerDeliveryListResponse response = deliveryService.getSellerDeliveries(sellerId, groupBuyId);
        return ResponseEntity.ok(ApiResponse.success(response, TraceIdUtils.from(httpRequest)));
    }

    @PutMapping("/deliveries/{deliveryId}/invoice")
    public ResponseEntity<ApiResponse<DeliveryResponse>> registerInvoice(
            @AuthenticationPrincipal Long sellerId,
            @PathVariable Long deliveryId,
            @Valid @RequestBody DeliveryInvoiceRequest request,
            HttpServletRequest httpRequest) {
        DeliveryResponse response = deliveryService.registerInvoice(sellerId, deliveryId, request);
        return ResponseEntity.ok(ApiResponse.success(response, TraceIdUtils.from(httpRequest)));
    }

    @PatchMapping("/deliveries/{deliveryId}/status")
    public ResponseEntity<ApiResponse<DeliveryResponse>> updateStatus(
            @AuthenticationPrincipal Long sellerId,
            @PathVariable Long deliveryId,
            @Valid @RequestBody DeliveryStatusUpdateRequest request,
            HttpServletRequest httpRequest) {
        DeliveryResponse response = deliveryService.updateStatus(sellerId, deliveryId, request.status());
        return ResponseEntity.ok(ApiResponse.success(response, TraceIdUtils.from(httpRequest)));
    }

    @PatchMapping("/group-buys/{groupBuyId}/deliveries/status")
    public ResponseEntity<ApiResponse<DeliveryBulkStatusResponse>> bulkUpdateStatus(
            @AuthenticationPrincipal Long sellerId,
            @PathVariable Long groupBuyId,
            @Valid @RequestBody DeliveryStatusUpdateRequest request,
            HttpServletRequest httpRequest) {
        DeliveryBulkStatusResponse response =
                deliveryService.bulkUpdateStatus(sellerId, groupBuyId, request.status());
        return ResponseEntity.ok(ApiResponse.success(response, TraceIdUtils.from(httpRequest)));
    }
}
