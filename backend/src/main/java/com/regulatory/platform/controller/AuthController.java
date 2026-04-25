package com.regulatory.platform.controller;

import com.regulatory.platform.dto.request.LoginRequest;
import com.regulatory.platform.dto.response.ApiResponse;
import com.regulatory.platform.dto.response.AuthResponse;
import com.regulatory.platform.entity.User;
import com.regulatory.platform.repository.UserRepository;
import com.regulatory.platform.security.JwtService;
import com.regulatory.platform.security.LoginRateLimitService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final LoginRateLimitService loginRateLimitService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request,
                                                           HttpServletRequest servletRequest) {
        String clientIp = extractClientIp(servletRequest);
        loginRateLimitService.assertAllowed(request.email(), clientIp);
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (BadCredentialsException ex) {
            loginRateLimitService.onFailedLogin(request.email(), clientIp);
            throw ex;
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.email());
        String token = jwtService.generateToken(userDetails);
        loginRateLimitService.onSuccessfulLogin(request.email(), clientIp);

        User user = userRepository.findByEmail(request.email()).orElseThrow();

        return ResponseEntity.ok(ApiResponse.ok(new AuthResponse(
                token,
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getId()
        )));
    }

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
