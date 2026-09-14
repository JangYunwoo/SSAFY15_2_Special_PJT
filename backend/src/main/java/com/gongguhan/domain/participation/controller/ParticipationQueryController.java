package com.gongguhan.domain.participation.controller;

import com.gongguhan.domain.participation.dto.ParticipationDetailResponse;
import com.gongguhan.domain.participation.dto.ParticipationSummaryResponse;
import com.gongguhan.domain.participation.service.ParticipationQueryService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.PageResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 구매자 참여 내역 조회 API (PAR-01·02). */
@RestController
@RequestMapping("/users/me/participations")
public class ParticipationQueryController {

    private final ParticipationQueryService participationQueryService;

    public ParticipationQueryController(ParticipationQueryService participationQueryService) {
        this.participationQueryService = participationQueryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ParticipationSummaryResponse>>> getMyParticipations(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        Page<ParticipationSummaryResponse> result =
                participationQueryService.getMyParticipations(userId, status, page, size);
        return ResponseEntity.ok(ApiResponse.success(
                result.getContent(), PageResponse.from(result), TraceIdUtils.from(httpRequest)));
    }

    @GetMapping("/{participationId}")
    public ResponseEntity<ApiResponse<ParticipationDetailResponse>> getMyParticipationDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long participationId,
            HttpServletRequest httpRequest) {
        ParticipationDetailResponse response =
                participationQueryService.getMyParticipationDetail(userId, participationId);
        return ResponseEntity.ok(ApiResponse.success(response, TraceIdUtils.from(httpRequest)));
    }
}
