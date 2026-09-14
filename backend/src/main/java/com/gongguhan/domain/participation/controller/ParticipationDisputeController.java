package com.gongguhan.domain.participation.controller;

import com.gongguhan.domain.participation.dto.DisputeRequest;
import com.gongguhan.domain.participation.service.ParticipationDisputeService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 구매자 이의 제기 API (CFM-02). */
@RestController
public class ParticipationDisputeController {

    private final ParticipationDisputeService participationDisputeService;

    public ParticipationDisputeController(ParticipationDisputeService participationDisputeService) {
        this.participationDisputeService = participationDisputeService;
    }

    @PostMapping("/users/me/participations/{participationId}/disputes")
    public ResponseEntity<ApiResponse<Void>> submitDispute(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long participationId,
            @Valid @RequestBody DisputeRequest request,
            HttpServletRequest httpRequest) {
        participationDisputeService.submitDispute(userId, participationId, request);
        return ResponseEntity.ok(ApiResponse.success(null, TraceIdUtils.from(httpRequest)));
    }
}
