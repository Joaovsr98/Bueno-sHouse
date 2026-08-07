package com.restaurante.sistema.modules.customers.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record CustomerRequest(
        @NotBlank String fullName,
        @NotBlank String phone,
        String email,
        String document,
        LocalDate birthDate
) {}
