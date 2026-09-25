package com.restaurante.sistema.modules.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cadastro publico de Cliente (RF-004). Sempre cria um usuario com perfil CLIENTE. */
public record RegisterRequest(
        @NotBlank(message = "O nome e obrigatorio")
        String fullName,

        @NotBlank(message = "O e-mail e obrigatorio")
        @Email(message = "E-mail invalido")
        String email,

        @NotBlank(message = "A senha e obrigatoria")
        @Size(min = 6, message = "A senha precisa ter pelo menos 6 caracteres")
        String password,

        @NotBlank(message = "O telefone e obrigatorio")
        String phone
) {}
