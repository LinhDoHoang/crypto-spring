package com.crypto.crypto.feature.users.dto;

import com.crypto.crypto.entities.UsersEntity;

public record UserResponse(
        Long id,
        String email,
        boolean enabled
) {
    public static UserResponse from(UsersEntity user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                Boolean.TRUE.equals(user.getEnabled())
        );
    }
}
