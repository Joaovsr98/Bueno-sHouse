package com.restaurante.sistema.modules.notifications.repository;

import com.restaurante.sistema.modules.notifications.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(Long userId);
}
