package com.regulatory.platform.controller;

import com.regulatory.platform.dto.response.NotificationResponse;
import com.regulatory.platform.entity.User;
import com.regulatory.platform.enums.NotificationType;
import com.regulatory.platform.enums.UserRole;
import com.regulatory.platform.repository.UserRepository;
import com.regulatory.platform.security.JwtService;
import com.regulatory.platform.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock private NotificationService notificationService;
    @Mock private UserRepository userRepository;
    @Mock private JwtService jwtService;
    @Mock private UserDetailsService userDetailsService;

    @InjectMocks
    private NotificationController notificationController;

    @Test
    void stream_usesPrincipalWhenPresent() {
        UserDetails principal = new org.springframework.security.core.userdetails.User(
                "officer@test.gov.sg", "x", List.of());
        User user = User.builder().id(1L).email("officer@test.gov.sg").role(UserRole.OFFICER).build();
        SseEmitter emitter = new SseEmitter();
        when(userRepository.findByEmail("officer@test.gov.sg")).thenReturn(Optional.of(user));
        when(notificationService.subscribe(user)).thenReturn(emitter);

        SseEmitter result = notificationController.stream(principal, null);

        assertEquals(emitter, result);
        verify(notificationService).subscribe(user);
    }

    @Test
    void stream_usesTokenWhenPrincipalMissing() {
        User user = User.builder().id(2L).email("operator@test.com").role(UserRole.OPERATOR).build();
        UserDetails details = new org.springframework.security.core.userdetails.User("operator@test.com", "x", List.of());
        SseEmitter emitter = new SseEmitter();
        when(jwtService.extractUsername("jwt-token")).thenReturn("operator@test.com");
        when(userDetailsService.loadUserByUsername("operator@test.com")).thenReturn(details);
        when(jwtService.isTokenValid("jwt-token", details)).thenReturn(true);
        when(userRepository.findByEmail("operator@test.com")).thenReturn(Optional.of(user));
        when(notificationService.subscribe(user)).thenReturn(emitter);

        SseEmitter result = notificationController.stream(null, "jwt-token");

        assertEquals(emitter, result);
        verify(notificationService).subscribe(user);
    }

    @Test
    void stream_missingToken_throws() {
        assertThrows(IllegalArgumentException.class, () -> notificationController.stream(null, ""));
    }

    @Test
    void stream_invalidToken_throws() {
        UserDetails details = new org.springframework.security.core.userdetails.User("operator@test.com", "x", List.of());
        when(jwtService.extractUsername("bad-token")).thenReturn("operator@test.com");
        when(userDetailsService.loadUserByUsername("operator@test.com")).thenReturn(details);
        when(jwtService.isTokenValid("bad-token", details)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> notificationController.stream(null, "bad-token"));
    }

    @Test
    void list_and_markAllRead_resolvePrincipalAndCallService() {
        UserDetails principal = new org.springframework.security.core.userdetails.User(
                "operator@test.com", "x", List.of());
        User user = User.builder().id(2L).email("operator@test.com").role(UserRole.OPERATOR).build();
        when(userRepository.findByEmail("operator@test.com")).thenReturn(Optional.of(user));
        when(notificationService.getForUser(user)).thenReturn(List.of(
                new NotificationResponse(1L, 9L, "REF-9", NotificationType.STATUS_CHANGE, "updated", false, LocalDateTime.now())
        ));

        var listResp = notificationController.list(principal);
        assertEquals(200, listResp.getStatusCode().value());
        verify(notificationService).getForUser(user);

        var markResp = notificationController.markAllRead(principal);
        assertEquals(200, markResp.getStatusCode().value());
        verify(notificationService).markAllRead(user);
    }
}
