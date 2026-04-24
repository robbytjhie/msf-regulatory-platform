package com.regulatory.platform.dto.response;

import com.regulatory.platform.enums.UserRole;

public record AuthResponse(
        String token,
        String email,
        String fullName,
        UserRole role,
        Long userId
) {}
