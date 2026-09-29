package com.restaurante.sistema.modules.identity.dto;

import jakarta.validation.constraints.Size;

/** Campos opcionais: so o que vier preenchido e alterado. */
public record UserUpdateRequest(
        String profileName,
        Boolean active,
        @Size(min = 8, max = 72) String newPassword
) {}
