package com.regulatory.platform.service;

import com.regulatory.platform.dto.response.NotificationResponse;
import com.regulatory.platform.entity.Application;
import com.regulatory.platform.entity.User;
import com.regulatory.platform.enums.NotificationType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface NotificationService {
    void create(User recipient, Application application, NotificationType type, String message);
    List<NotificationResponse> getForUser(User user);
    void markAllRead(User user);
    SseEmitter subscribe(User user);
}
