package com.gongguhan.domain.admin.controller;

import com.gongguhan.domain.admin.dto.AdminDtos.AuditLogResponse;
import com.gongguhan.domain.admin.dto.AdminDtos.ExternalApiLogResponse;
import com.gongguhan.domain.admin.repository.AuditLogRepository;
import com.gongguhan.domain.payment.card.repository.SsafyApiLogRepository;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.PageResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 운영 로그 조회 (ADM-05 외부 금융 API 호출 이력, ADM-07 감사 로그). */
@RestController
public class AdminLogController {

    private final SsafyApiLogRepository ssafyApiLogRepository;
    private final AuditLogRepository auditLogRepository;

    public AdminLogController(SsafyApiLogRepository ssafyApiLogRepository,
                              AuditLogRepository auditLogRepository) {
        this.ssafyApiLogRepository = ssafyApiLogRepository;
        this.auditLogRepository = auditLogRepository;
    }

    /** ADM-05 외부 금융 API 호출 결과 조회. */
    @GetMapping("/admin/external-api-logs")
    public ResponseEntity<ApiResponse<List<ExternalApiLogResponse>>> getExternalApiLogs(
            @PageableDefault(size = 20, sort = "logId", direction = Sort.Direction.DESC) Pageable pageable,
            HttpServletRequest httpRequest) {
        Page<ExternalApiLogResponse> page =
                ssafyApiLogRepository.findAll(pageable).map(ExternalApiLogResponse::from);
        return ResponseEntity.ok(ApiResponse.success(
                page.getContent(), PageResponse.from(page), TraceIdUtils.from(httpRequest)));
    }

    /** ADM-07 관리자 정책·상태 변경 감사로그 조회. */
    @GetMapping("/admin/audit-logs")
    public ResponseEntity<ApiResponse<List<AuditLogResponse>>> getAuditLogs(
            @PageableDefault(size = 20, sort = "logId", direction = Sort.Direction.DESC) Pageable pageable,
            HttpServletRequest httpRequest) {
        Page<AuditLogResponse> page =
                auditLogRepository.findAll(pageable).map(AuditLogResponse::from);
        return ResponseEntity.ok(ApiResponse.success(
                page.getContent(), PageResponse.from(page), TraceIdUtils.from(httpRequest)));
    }
}
