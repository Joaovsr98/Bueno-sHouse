package com.restaurante.sistema.modules.inventory.dto;

import jakarta.validation.constraints.NotNull;

public record SupplierActiveRequest(@NotNull Boolean active) {}
