package com.regulatory.platform.controller;

import com.regulatory.platform.dto.response.ApiResponse;
import com.regulatory.platform.dto.response.NotificationResponse;
import com.regulatory.platform.entity.User;
import com.regulatory.platform.repository.UserRepository;
import com.regulatory.platform.security.JwtService;
import com.regulatory.platform.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> list(
            @AuthenticationPrincipal UserDetails principal) {
        User user = resolveUser(principal);
        return ResponseEntity.ok(ApiResponse.ok(notificationService.getForUser(user)));
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(required = false) String token) {
        User user = resolveUser(principal, token);
        return notificationService.subscribe(user);
    }

    @PatchMapping("/me/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllRead(
            @AuthenticationPrincipal UserDetails principal) {
        User user = resolveUser(principal);
        notificationService.markAllRead(user);
        return ResponseEntity.ok(ApiResponse.ok("Notifications marked as read", null));
    }

    private User resolveUser(UserDetails principal) {
        return userRepository.findByEmail(principal.getUsername()).orElseThrow();
    }

    private User resolveUser(UserDetails principal, String token) {
        if (principal != null) {
            return resolveUser(principal);
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Authentication token is required for notification stream");
        }
        String email = jwtService.extractUsername(token);
        UserDetails details = userDetailsService.loadUserByUsername(email);
        if (!jwtService.isTokenValid(token, details)) {
            throw new IllegalArgumentException("Invalid authentication token for notification stream");
        }
        return userRepository.findByEmail(email).orElseThrow();
    }
}
