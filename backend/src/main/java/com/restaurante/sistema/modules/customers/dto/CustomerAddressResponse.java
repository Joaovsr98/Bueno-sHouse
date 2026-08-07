package com.restaurante.sistema.modules.customers.dto;

public record CustomerAddressResponse(
        Long id,
        String label,
        String street,
        String number,
        String complement,
        String neighborhood,
        String city,
        String state,
        String zipCode,
        String referencePoint,
        boolean isDefault
) {}
