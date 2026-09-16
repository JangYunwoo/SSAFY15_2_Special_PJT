package com.gongguhan.domain.account.controller;

import com.gongguhan.domain.account.dto.CardResponse;
import com.gongguhan.domain.account.service.CardIssuanceService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 마이페이지 카드 조회·발급 API. 인증된 본인({@code /users/me})만 접근한다.
 *
 * <ul>
 *   <li>{@code GET /users/me/card} - 발급된 카드 조회(없으면 hasCard=false)</li>
 *   <li>{@code POST /users/me/card} - 카드 발급(이미 있으면 그 카드 반환)</li>
 * </ul>
 */
@RestController
@RequestMapping("/users/me/card")
public class UserCardController {

    private final CardIssuanceService cardIssuanceService;

    public UserCardController(CardIssuanceService cardIssuanceService) {
        this.cardIssuanceService = cardIssuanceService;
    }

    /** 내 카드 조회. 발급 전이면 hasCard=false. */
    @GetMapping
    public ResponseEntity<ApiResponse<CardResponse>> getCard(
            @AuthenticationPrincipal Long userId, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                cardIssuanceService.getMyCard(userId), TraceIdUtils.from(httpRequest)));
    }

    /** 카드 발급. 계좌가 없으면 개설 후 발급하고, 이미 카드가 있으면 그 카드를 돌려준다. */
    @PostMapping
    public ResponseEntity<ApiResponse<CardResponse>> issueCard(
            @AuthenticationPrincipal Long userId, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                cardIssuanceService.issueCard(userId), TraceIdUtils.from(httpRequest)));
    }
}
