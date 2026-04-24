package com.regulatory.platform.controller;

import com.regulatory.platform.dto.request.LoginRequest;
import com.regulatory.platform.dto.response.ApiResponse;
import com.regulatory.platform.dto.response.AuthResponse;
import com.regulatory.platform.entity.User;
import com.regulatory.platform.repository.UserRepository;
import com.regulatory.platform.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
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

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.email());
        String token = jwtService.generateToken(userDetails);

        User user = userRepository.findByEmail(request.email()).orElseThrow();

        return ResponseEntity.ok(ApiResponse.ok(new AuthResponse(
                token,
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getId()
        )));
    }
}
