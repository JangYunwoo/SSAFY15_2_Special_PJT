package com.gongguhan.domain.participation.controller;

import com.gongguhan.domain.participation.service.ParticipationCancelService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** PAR-03 참여 취소. {@code /users/me/participations}는 인증된 본인만 접근한다. */
@RestController
@RequestMapping("/users/me/participations")
public class ParticipationCancelController {

    private final ParticipationCancelService participationCancelService;

    public ParticipationCancelController(ParticipationCancelService participationCancelService) {
        this.participationCancelService = participationCancelService;
    }

    /** PAR-03 모집 완료 전 참여 취소·환불 개시. */
    @PostMapping("/{participationId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long participationId, HttpServletRequest httpRequest) {
        participationCancelService.cancel(userId, participationId);
        return ResponseEntity.ok(ApiResponse.<Void>success(null, TraceIdUtils.from(httpRequest)));
    }
}
