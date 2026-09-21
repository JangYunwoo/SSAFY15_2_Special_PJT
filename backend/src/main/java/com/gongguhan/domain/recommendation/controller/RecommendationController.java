package com.gongguhan.domain.recommendation.controller;

import com.gongguhan.domain.recommendation.dto.RecommendationEventRequest;
import com.gongguhan.domain.recommendation.dto.RecommendationResponse;
import com.gongguhan.domain.recommendation.dto.RecommendationSessionEndRequest;
import com.gongguhan.domain.recommendation.service.RecommendationService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/group-buys")
    public ResponseEntity<ApiResponse<RecommendationResponse>> recommend(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "8") int size,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                recommendationService.recommend(userId, size), TraceIdUtils.from(httpRequest)));
    }

    @PostMapping("/events")
    public ResponseEntity<ApiResponse<Void>> recordEvents(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody RecommendationEventRequest request,
            HttpServletRequest httpRequest) {
        recommendationService.recordEvents(userId, request);
        return ResponseEntity.ok(ApiResponse.success(null, TraceIdUtils.from(httpRequest)));
    }

    @PostMapping("/sessions/end")
    public ResponseEntity<ApiResponse<Void>> endSession(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody RecommendationSessionEndRequest request,
            HttpServletRequest httpRequest) {
        recommendationService.endSession(userId, request.sessionId());
        return ResponseEntity.ok(ApiResponse.success(null, TraceIdUtils.from(httpRequest)));
    }
}
