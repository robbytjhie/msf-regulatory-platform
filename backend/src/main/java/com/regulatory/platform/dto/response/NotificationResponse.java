package com.regulatory.platform.dto.response;

import com.regulatory.platform.entity.Notification;
import com.regulatory.platform.enums.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        Long applicationId,
        String referenceNumber,
        NotificationType type,
        String message,
        boolean read,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getApplication().getId(),
                n.getApplication().getReferenceNumber(),
                n.getType(),
                n.getMessage(),
                n.isRead(),
                n.getCreatedAt()
        );
    }
}
