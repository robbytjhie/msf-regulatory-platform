package com.regulatory.platform.service;

import com.regulatory.platform.dto.response.NotificationResponse;
import com.regulatory.platform.entity.Application;
import com.regulatory.platform.entity.Notification;
import com.regulatory.platform.entity.User;
import com.regulatory.platform.enums.ApplicationStatus;
import com.regulatory.platform.enums.NotificationType;
import com.regulatory.platform.enums.UserRole;
import com.regulatory.platform.repository.NotificationRepository;
import com.regulatory.platform.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Test
    void create_persistsUnreadNotification() {
        User recipient = User.builder()
                .email("operator@test.com")
                .role(UserRole.OPERATOR)
                .fullName("Operator")
                .password("encoded")
                .build();

        Application app = Application.builder()
                .id(99L)
                .referenceNumber("TEST-99")
                .status(ApplicationStatus.UNDER_REVIEW)
                .businessName("Biz")
                .operator(recipient)
                .build();

        notificationService.create(recipient, app, NotificationType.STATUS_CHANGE, "Status updated");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertEquals(recipient, saved.getUser());
        assertEquals(app, saved.getApplication());
        assertEquals(NotificationType.STATUS_CHANGE, saved.getType());
        assertEquals("Status updated", saved.getMessage());
        assertFalse(saved.isRead());
    }

    @Test
    void getForUser_mapsNotificationsToResponse() {
        User recipient = User.builder()
                .email("operator@test.com")
                .role(UserRole.OPERATOR)
                .fullName("Operator")
                .password("encoded")
                .build();

        Application app = Application.builder()
                .id(7L)
                .referenceNumber("TEST-7")
                .status(ApplicationStatus.PENDING_APPROVAL)
                .businessName("Biz")
                .operator(recipient)
                .build();

        Notification notification = Notification.builder()
                .id(11L)
                .user(recipient)
                .application(app)
                .type(NotificationType.CHECKLIST_RESPONSE)
                .message("Checklist item responded")
                .read(false)
                .build();

        when(notificationRepository.findByUserOrderByCreatedAtDesc(recipient)).thenReturn(List.of(notification));

        List<NotificationResponse> result = notificationService.getForUser(recipient);

        assertEquals(1, result.size());
        assertEquals(11L, result.get(0).id());
        assertEquals(7L, result.get(0).applicationId());
        assertEquals("TEST-7", result.get(0).referenceNumber());
    }

    @Test
    void markAllRead_updatesEachNotificationFlag() {
        User recipient = User.builder()
                .email("officer@test.gov.sg")
                .role(UserRole.OFFICER)
                .fullName("Officer")
                .password("encoded")
                .build();

        Notification n1 = Notification.builder().read(false).build();
        Notification n2 = Notification.builder().read(false).build();
        when(notificationRepository.findByUserOrderByCreatedAtDesc(recipient)).thenReturn(List.of(n1, n2));

        notificationService.markAllRead(recipient);

        assertTrue(n1.isRead());
        assertTrue(n2.isRead());
    }
}
