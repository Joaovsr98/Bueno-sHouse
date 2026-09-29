package com.restaurante.sistema.modules.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Criacao de usuario interno (RF-041). O perfil CLIENTE so nasce pelo cadastro publico. */
public record UserRequest(
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank String profileName
) {}
