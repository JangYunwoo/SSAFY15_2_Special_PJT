package com.gongguhan.domain.groupbuy.controller;

import com.gongguhan.domain.groupbuy.dto.GroupBuyJudgementResult;
import com.gongguhan.domain.groupbuy.service.GroupBuyRecruitmentService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/jobs/group-buy-recruitment")
public class AdminGroupBuyJobController {

    private final GroupBuyRecruitmentService groupBuyRecruitmentService;

    public AdminGroupBuyJobController(GroupBuyRecruitmentService groupBuyRecruitmentService) {
        this.groupBuyRecruitmentService = groupBuyRecruitmentService;
    }

    @PostMapping("/run")
    public ResponseEntity<ApiResponse<GroupBuyJudgementResult>> run(HttpServletRequest request) {
        GroupBuyJudgementResult result = groupBuyRecruitmentService.judgeCandidates();
        return ResponseEntity.ok(ApiResponse.success(result, TraceIdUtils.from(request)));
    }
}
