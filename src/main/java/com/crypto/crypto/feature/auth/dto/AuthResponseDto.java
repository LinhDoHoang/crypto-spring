package com.crypto.crypto.feature.auth.dto;

import com.crypto.crypto.feature.users.dto.UserResponse;

public record AuthResponseDto(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {
}
