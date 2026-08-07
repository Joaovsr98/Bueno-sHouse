package com.restaurante.sistema.modules.notifications.service;

import com.restaurante.sistema.common.exception.ResourceNotFoundException;
import com.restaurante.sistema.modules.notifications.domain.Notification;
import com.restaurante.sistema.modules.notifications.dto.NotificationResponse;
import com.restaurante.sistema.modules.notifications.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Etapa 13 (Fase 2) - notificacoes internas apenas (decisao da Etapa 1:
 * integracoes externas como WhatsApp/e-mail/SMS/push nao entram sem
 * aprovacao explicita - nao implementadas).
 */
@Service
public class NotificationService {

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void notify(Long userId, String title, String message) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setCreatedAt(Instant.now());
        repository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listUnread(Long userId) {
        return repository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId).stream()
                .map(n -> new NotificationResponse(n.getId(), n.getTitle(), n.getMessage(), n.getCreatedAt(), n.getReadAt() != null))
                .toList();
    }

    @Transactional
    public void markRead(Long id) {
        Notification notification = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
        notification.setReadAt(Instant.now());
        repository.save(notification);
    }
}
