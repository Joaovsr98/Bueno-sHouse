package com.restaurante.sistema.modules.customers.dto;

import java.time.LocalDate;
import java.util.List;

public record CustomerResponse(
        Long id,
        String fullName,
        String phone,
        String email,
        String document,
        LocalDate birthDate,
        List<CustomerAddressResponse> addresses
) {}
