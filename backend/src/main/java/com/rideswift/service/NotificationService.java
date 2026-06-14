package com.rideswift.service;

import com.rideswift.dto.response.NotificationResponse;
import com.rideswift.exception.ForbiddenException;
import com.rideswift.exception.ResourceNotFoundException;
import com.rideswift.model.Notification;
import com.rideswift.model.NotificationType;
import com.rideswift.repository.NotificationRepository;
import com.rideswift.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    /** Persists an in-app notification for a user. Joins the caller's transaction. */
    @Transactional
    public Notification create(UUID userId, String message, NotificationType type) {
        Notification notification = Notification.builder()
                .user(userRepository.getReferenceById(userId))
                .message(message)
                .type(type)
                .read(false)
                .build();
        return notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listForUser(UUID userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional
    public NotificationResponse markRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));
        if (!notification.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Not your notification");
        }
        notification.setRead(true);
        return NotificationResponse.from(notificationRepository.save(notification));
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return notificationRepository.markAllReadForUser(userId);
    }
}
