package com.restaurante.sistema.modules.identity.dto;

import com.restaurante.sistema.modules.identity.domain.User;

public record UserResponse(String publicId, Long id, String email, String profileName, boolean active) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getPublicId(), u.getId(), u.getEmail(), u.getProfile().getName(), u.isActive());
    }
}
