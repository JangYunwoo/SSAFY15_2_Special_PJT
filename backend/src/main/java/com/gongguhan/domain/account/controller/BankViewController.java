package com.gongguhan.domain.account.controller;

import com.gongguhan.domain.account.dto.BankViewDtos.AccountView;
import com.gongguhan.domain.account.dto.BankViewDtos.CardView;
import com.gongguhan.domain.account.service.BankViewService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가상 은행 조회 콘솔 API(시연용). 인증된 본인({@code /users/me})의 SSAFY 카드·계좌를 조회한다.
 *
 * <ul>
 *   <li>{@code GET /users/me/bank/card} - 카드 이용내역 + 청구예정액(결제 확인)</li>
 *   <li>{@code GET /users/me/bank/account} - 계좌 잔액 + 거래내역(환불 입금 확인)</li>
 * </ul>
 */
@RestController
@RequestMapping("/users/me/bank")
public class BankViewController {

    private final BankViewService bankViewService;

    public BankViewController(BankViewService bankViewService) {
        this.bankViewService = bankViewService;
    }

    /** 카드 이용내역 조회. */
    @GetMapping("/card")
    public ResponseEntity<ApiResponse<CardView>> card(
            @AuthenticationPrincipal Long userId, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                bankViewService.getCardView(userId), TraceIdUtils.from(httpRequest)));
    }

    /** 계좌 잔액·거래내역 조회. */
    @GetMapping("/account")
    public ResponseEntity<ApiResponse<AccountView>> account(
            @AuthenticationPrincipal Long userId, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                bankViewService.getAccountView(userId), TraceIdUtils.from(httpRequest)));
    }
}
