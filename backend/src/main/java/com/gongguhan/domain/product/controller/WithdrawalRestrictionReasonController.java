package com.gongguhan.domain.product.controller;

import com.gongguhan.domain.product.dto.WithdrawalRestrictionReasonResponse;
import com.gongguhan.domain.product.service.WithdrawalRestrictionService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/withdrawal-restriction-reasons")
public class WithdrawalRestrictionReasonController {

    private final WithdrawalRestrictionService service;

    public WithdrawalRestrictionReasonController(WithdrawalRestrictionService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<WithdrawalRestrictionReasonResponse>>> getActiveReasons(
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.getActiveReasons(), TraceIdUtils.from(request)));
    }
}
