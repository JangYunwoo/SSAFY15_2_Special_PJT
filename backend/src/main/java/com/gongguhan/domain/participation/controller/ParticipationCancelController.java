package com.gongguhan.domain.participation.controller;

import com.gongguhan.domain.participation.dto.ParticipationCancelRequest;
import com.gongguhan.domain.participation.service.ParticipationCancelService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /**
     * PAR-03 모집 완료 전 참여 취소·환불 개시.
     *
     * <p>카드 결제 건은 카드 승인 취소를 위해 바디로 카드번호·CVC 를 받는다(선택). 다른
     * 결제수단이면 바디 없이 호출한다.
     */
    @PostMapping("/{participationId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long participationId,
            @Valid @RequestBody(required = false) ParticipationCancelRequest request,
            HttpServletRequest httpRequest) {
        String cardNumber = request == null ? null : request.cardNumber();
        String cvc = request == null ? null : request.cvc();
        participationCancelService.cancel(userId, participationId, cardNumber, cvc);
        return ResponseEntity.ok(ApiResponse.<Void>success(null, TraceIdUtils.from(httpRequest)));
    }
}
