package com.restaurante.sistema.modules.customers.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CustomerAddressRequest(
        String label,
        @NotBlank String street,
        @NotBlank String number,
        String complement,
        @NotBlank String neighborhood,
        @NotBlank String city,
        @NotBlank String state,
        /** CEP com 8 digitos, com ou sem hifen (gravado sempre como 00000-000). */
        @NotBlank @Pattern(regexp = "\\d{5}-?\\d{3}", message = "CEP invalido: use o formato 00000-000") String zipCode,
        String referencePoint,
        Boolean isDefault
) {}
