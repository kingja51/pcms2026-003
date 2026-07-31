package com.gonet.primary.notification.controller;

import com.gonet.common.dto.ApiResponse;
import com.gonet.primary.notification.service.NotificationService;
import com.gonet.primary.system.login.dto.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 알림 REST API — 헤더 종 뱃지 폴링용.
 *
 * <p>{@code GET /api/v1/notification/unread-count} → {@code {ok:true, data:{unread:N}}}
 * 인증 필수. 비인증이면 SecurityFilter 가 401 차단. 30초 간격 폴링 권장.
 */
@Tag(name = "Notification",
    description = "헤더 종 뱃지 폴링용 알림 API (§0.41). 30초 간격 폴링 권장 — " +
        "탭 visibilityState=hidden 시 정지, visible 시 즉시 1회 + 재개.")
@RestController
@RequestMapping("/api/v1/notification")
public class NotificationApiController {

    private final NotificationService service;

    public NotificationApiController(NotificationService service) {
        this.service = service;
    }

    @Operation(
        summary = "미확인 알림 카운트",
        description = "현재 인증 사용자의 미확인 알림 수. 비인증이면 SecurityFilter 가 401 차단 — " +
            "본 메서드는 인증 통과 후만 실행. JS bell 위젯이 결과의 `unread` 값으로 빨간 배지 표시 " +
            "(0 → hidden, 1~99 → 숫자, ≥100 → '99+').")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            description = "성공 — `{ok:true, data:{unread:N}}`"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
            description = "비로그인 — bell 위젯은 silent 처리")
    })
    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Object>> unreadCount(
            @AuthenticationPrincipal CustomUserDetails user,
            Authentication auth) {
        if (user == null && auth != null && auth.getPrincipal() instanceof CustomUserDetails p) {
            user = p;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        if (user == null) {
            body.put("unread", 0);
            return ApiResponse.ok(body);
        }
        body.put("unread", service.unreadCount(user.getUserId()));
        return ApiResponse.ok(body);
    }
}
