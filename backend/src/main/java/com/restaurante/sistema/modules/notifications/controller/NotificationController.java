package com.restaurante.sistema.modules.notifications.controller;

import com.restaurante.sistema.modules.identity.security.CurrentUserProvider;
import com.restaurante.sistema.modules.notifications.dto.NotificationResponse;
import com.restaurante.sistema.modules.notifications.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUserProvider currentUserProvider;

    public NotificationController(NotificationService notificationService, CurrentUserProvider currentUserProvider) {
        this.notificationService = notificationService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/unread")
    public List<NotificationResponse> listUnread() {
        return notificationService.listUnread(currentUserProvider.getCurrentUserId());
    }

    @PostMapping("/{id}/read")
    public void markRead(@PathVariable Long id) {
        notificationService.markRead(id);
    }
}
