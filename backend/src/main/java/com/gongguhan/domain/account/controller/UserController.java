package com.gongguhan.domain.account.controller;

import com.gongguhan.domain.account.dto.UserProfileResponse;
import com.gongguhan.domain.account.dto.UserUpdateRequest;
import com.gongguhan.domain.account.service.UserService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 사용자 프로필 API (USR-01·02). */
@RestController
@RequestMapping("/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMe(
            @AuthenticationPrincipal Long userId, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.getProfile(userId), TraceIdUtils.from(httpRequest)));
    }

    @PatchMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMe(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserUpdateRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.updateProfile(userId, request), TraceIdUtils.from(httpRequest)));
    }
}
