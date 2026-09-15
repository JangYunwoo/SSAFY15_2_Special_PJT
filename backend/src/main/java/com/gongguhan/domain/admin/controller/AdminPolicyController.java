package com.gongguhan.domain.admin.controller;

import com.gongguhan.domain.admin.dto.AdminDtos.PolicyResponse;
import com.gongguhan.domain.admin.dto.AdminDtos.PolicyUpdateRequest;
import com.gongguhan.domain.admin.service.AdminPolicyService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 정책 조회·변경 (ADM-03·04). {@code /admin/**}은 SecurityConfig가 ROLE_ADMIN으로 막는다. */
@RestController
@RequestMapping("/admin/policies")
public class AdminPolicyController {

    private final AdminPolicyService adminPolicyService;

    public AdminPolicyController(AdminPolicyService adminPolicyService) {
        this.adminPolicyService = adminPolicyService;
    }

    /** ADM-03 정책값 전체 조회. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<PolicyResponse>>> getPolicies(HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                adminPolicyService.getPolicies(), TraceIdUtils.from(httpRequest)));
    }

    /** ADM-04 정책값 변경(감사로그 기록). */
    @PatchMapping("/{policyKey}")
    public ResponseEntity<ApiResponse<PolicyResponse>> updatePolicy(
            @PathVariable String policyKey,
            @Valid @RequestBody PolicyUpdateRequest request,
            @AuthenticationPrincipal Long adminId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                adminPolicyService.updatePolicy(policyKey, request.value(), adminId),
                TraceIdUtils.from(httpRequest)));
    }
}
