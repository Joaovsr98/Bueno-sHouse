package com.restaurante.sistema.modules.delivery.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmDeliveryRequest(
        @NotBlank(message = "O codigo de confirmacao e obrigatorio") String confirmationCode,
        @NotBlank(message = "O nome de quem recebeu e obrigatorio") String receivedByName
) {}
