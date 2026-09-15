package com.gongguhan.domain.notification.controller;

import com.gongguhan.domain.notification.dto.NotificationDtos.NotificationResponse;
import com.gongguhan.domain.notification.dto.NotificationDtos.UnreadCountResponse;
import com.gongguhan.domain.notification.service.NotificationService;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인앱 알림 API (NOTI-01~03). {@code /notifications}는 인증된 사용자만 접근한다. */
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** NOTI-01 내 알림 목록 조회. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getNotifications(
            @AuthenticationPrincipal Long userId,
            @PageableDefault(size = 20, sort = "notificationId", direction = Sort.Direction.DESC) Pageable pageable,
            HttpServletRequest httpRequest) {
        Page<NotificationResponse> page = notificationService.getMyNotifications(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(
                page.getContent(), PageResponse.from(page), TraceIdUtils.from(httpRequest)));
    }

    /** NOTI-02 읽지 않은 알림 수 조회. */
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadCount(
            @AuthenticationPrincipal Long userId, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                new UnreadCountResponse(notificationService.getUnreadCount(userId)),
                TraceIdUtils.from(httpRequest)));
    }

    /** NOTI-03 알림 읽음 처리. */
    @PostMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long notificationId, HttpServletRequest httpRequest) {
        notificationService.markAsRead(userId, notificationId);
        return ResponseEntity.ok(ApiResponse.<Void>success(null, TraceIdUtils.from(httpRequest)));
    }
}
