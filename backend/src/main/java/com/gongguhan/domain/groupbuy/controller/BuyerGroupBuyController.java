package com.gongguhan.domain.groupbuy.controller;

import com.gongguhan.domain.groupbuy.dto.BuyerParticipationSummaryResponse;
import com.gongguhan.domain.groupbuy.service.GroupBuyService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.PageResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/users/me/participations", "/buyers/me/participations"})
public class BuyerGroupBuyController {

    private final GroupBuyService groupBuyService;

    public BuyerGroupBuyController(GroupBuyService groupBuyService) {
        this.groupBuyService = groupBuyService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BuyerParticipationSummaryResponse>>> getParticipations(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        Page<BuyerParticipationSummaryResponse> result =
                groupBuyService.getBuyerParticipations(userId, status, page, size);
        return ResponseEntity.ok(ApiResponse.success(
                result.getContent(), PageResponse.from(result), TraceIdUtils.from(httpRequest)));
    }
}
