package com.ecommerce.notification.services;

import com.ecommerce.notification.dto.NotificationResponse;
import com.ecommerce.notification.exceptions.NotificationNotFoundException;
import com.ecommerce.notification.models.Notification;
import com.ecommerce.notification.models.NotificationType;
import com.ecommerce.notification.repositories.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_MESSAGE_LENGTH = 1000;

    private final NotificationRepository notificationRepository;
    private final EmailService emailService;

    /**
     * Persists an in-app notification for the user and dispatches it through the email abstraction.
     * The userId is used as the recipient identifier since no real email addresses are available here.
     */
    @Transactional
    public NotificationResponse createAndSend(String userId, NotificationType type, String title,
                                              String message, String referenceId) {
        Notification saved = notificationRepository.save(Notification.builder()
                .userId(userId)
                .type(type != null ? type : NotificationType.GENERIC)
                .title(truncate(title, MAX_TITLE_LENGTH))
                .message(truncate(message, MAX_MESSAGE_LENGTH))
                .referenceId(referenceId)
                .isRead(false)
                .build());
        log.debug("Saved notification id={} type={} for user {}", saved.getId(), saved.getType(), userId);

        emailService.sendEmail(userId, saved.getTitle(), saved.getMessage());
        return NotificationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getForUser(String userId, boolean onlyUnread, Pageable pageable) {
        Page<Notification> page = onlyUnread
                ? notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(userId, false, pageable)
                : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return page.map(NotificationResponse::from);
    }

    /** Marks a single notification read. Returns 404 (not 403) for notifications owned by someone else. */
    @Transactional
    public NotificationResponse markRead(Long id, String userId) {
        Notification notification = notificationRepository.findById(id)
                .filter(n -> n.getUserId().equals(userId))
                .orElseThrow(() -> new NotificationNotFoundException(id));
        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notification = notificationRepository.save(notification);
        }
        return NotificationResponse.from(notification);
    }

    @Transactional
    public int markAllRead(String userId) {
        return notificationRepository.markAllRead(userId);
    }

    @Transactional(readOnly = true)
    public long countUnread(String userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
