package com.regulatory.platform.service.impl;

import com.regulatory.platform.dto.response.NotificationResponse;
import com.regulatory.platform.entity.Application;
import com.regulatory.platform.entity.Notification;
import com.regulatory.platform.entity.User;
import com.regulatory.platform.enums.NotificationType;
import com.regulatory.platform.repository.NotificationRepository;
import com.regulatory.platform.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    @Override
    public void create(User recipient, Application application, NotificationType type, String message) {
        Notification notification = Notification.builder()
                .user(recipient)
                .application(application)
                .type(type)
                .message(message)
                .read(false)
                .build();
        notificationRepository.save(notification);
        log.info("Notification created for {} on {}: {}", recipient.getEmail(), application.getReferenceNumber(), type);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getForUser(User user) {
        return notificationRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Override
    public void markAllRead(User user) {
        notificationRepository.findByUserOrderByCreatedAtDesc(user)
                .forEach(n -> n.setRead(true));
    }
}
