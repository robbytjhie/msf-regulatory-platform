package com.regulatory.platform.controller;

import com.regulatory.platform.dto.response.ApiResponse;
import com.regulatory.platform.dto.response.NotificationResponse;
import com.regulatory.platform.entity.User;
import com.regulatory.platform.repository.UserRepository;
import com.regulatory.platform.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> list(
            @AuthenticationPrincipal UserDetails principal) {
        User user = resolveUser(principal);
        return ResponseEntity.ok(ApiResponse.ok(notificationService.getForUser(user)));
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
}
