package com.restaurante.sistema.modules.customers.dto;

import jakarta.validation.constraints.NotBlank;

public record CustomerAddressRequest(
        String label,
        @NotBlank String street,
        @NotBlank String number,
        String complement,
        @NotBlank String neighborhood,
        @NotBlank String city,
        @NotBlank String state,
        @NotBlank String zipCode,
        String referencePoint,
        Boolean isDefault
) {}
