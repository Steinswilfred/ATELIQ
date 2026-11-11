package com.clothingplatform.notification;

import com.clothingplatform.common.ApiResponse;
import com.clothingplatform.security.CurrentUser;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/notifications")
public class NotificationController {
    private final NotificationService service;
    public NotificationController(NotificationService service) { this.service = service; }

    @GetMapping
    public ApiResponse<Map<String, Object>> list() { return ApiResponse.ok(service.list(CurrentUser.require().getId())); }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> read(@PathVariable long id) { service.markRead(CurrentUser.require().getId(), id); return ApiResponse.ok(null); }

    @PostMapping("/read-all")
    public ApiResponse<Void> readAll() { service.markAllRead(CurrentUser.require().getId()); return ApiResponse.ok(null); }
}
