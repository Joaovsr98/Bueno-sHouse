package com.restaurante.sistema.modules.identity.dto;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresInMinutes,
        UserSummary user
) {
    public record UserSummary(
            String publicId,
            String email,
            String profileName
    ) {}
}
