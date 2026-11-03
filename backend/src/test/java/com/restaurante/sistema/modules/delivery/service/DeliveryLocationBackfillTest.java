package com.restaurante.sistema.modules.delivery.service;

import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;

import static org.assertj.core.api.Assertions.assertThat;

/** Leitura do endereco gravado na entrega (o mesmo formato que o site le). */
class DeliveryLocationBackfillTest {

    @Test
    void readsStreetNumberNeighborhoodAndCepFromTheSnapshot() {
        Matcher m = DeliveryLocationBackfill.SNAPSHOT.matcher("Rua das Flores, 100 (apto 2) - Centro, Sao Paulo/SP - CEP 01000-000");
        assertThat(m.find()).isTrue();
        assertThat(m.group(1)).isEqualTo("Rua das Flores");
        assertThat(m.group(2)).isEqualTo("100");
        assertThat(m.group(3)).isEqualTo("Centro");
        assertThat(m.group(4)).isEqualTo("01000-000");
    }
}
