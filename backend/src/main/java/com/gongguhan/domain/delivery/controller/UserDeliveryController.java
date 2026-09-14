package com.gongguhan.domain.delivery.controller;

import com.gongguhan.domain.delivery.dto.DeliveryResponse;
import com.gongguhan.domain.delivery.service.DeliveryService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 구매자 배송 조회 API (DLV-05). */
@RestController
public class UserDeliveryController {

    private final DeliveryService deliveryService;

    public UserDeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @GetMapping("/users/me/participations/{participationId}/delivery")
    public ResponseEntity<ApiResponse<DeliveryResponse>> getDelivery(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long participationId,
            HttpServletRequest httpRequest) {
        DeliveryResponse response = deliveryService.getUserDelivery(userId, participationId);
        return ResponseEntity.ok(ApiResponse.success(response, TraceIdUtils.from(httpRequest)));
    }
}
