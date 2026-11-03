package com.restaurante.sistema.modules.customers.service;

import com.restaurante.sistema.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regra do CEP x bairro no servidor, com um ViaCEP falso (sem internet). */
class AddressCepValidatorTest {

    private static final Map<String, AddressCepValidator.CepInfo> CEPS = Map.of(
            "04144000", new AddressCepValidator.CepInfo("Vila da Saúde", "São Paulo"),
            "06786075", new AddressCepValidator.CepInfo("Jardim São Judas Tadeu", "Taboão da Serra"),
            "01001000", new AddressCepValidator.CepInfo("Sé", "São Paulo"),
            "01000000", new AddressCepValidator.CepInfo("", "São Paulo"));

    private final AddressCepValidator validator =
            new AddressCepValidator(true, digits -> Optional.ofNullable(CEPS.get(digits)));

    @Test
    void acceptsCepOfTheDeclaredNeighborhood() {
        assertThatCode(() -> validator.validate("04144-000", "Saúde")).doesNotThrowAnyException();
    }

    @Test
    void rejectsCepFromAnotherCity() {
        assertThatThrownBy(() -> validator.validate("06786-075", "Itaim Bibi"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Taboão da Serra");
    }

    @Test
    void rejectsCepFromAnotherNeighborhood() {
        assertThatThrownBy(() -> validator.validate("04144-000", "Itaim Bibi"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Vila da Saúde");
    }

    @Test
    void rejectsUnknownCep() {
        assertThatThrownBy(() -> validator.validate("99999-999", "Centro"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("nao encontrado");
    }

    @Test
    void correiosNamesMatchTheDeliveryList() {
        // os Correios chamam o Centro de "Se"
        assertThatCode(() -> validator.validate("01001-000", "Centro")).doesNotThrowAnyException();
        assertThat(AddressCepValidator.matches("Jardim Paulista", "Jardins")).isTrue();
    }

    @Test
    void generalCityCepWithoutNeighborhoodIsAccepted() {
        assertThatCode(() -> validator.validate("01000-000", "Centro")).doesNotThrowAnyException();
    }

    @Test
    void viaCepOfflineDoesNotBlockTheCustomer() {
        AddressCepValidator offline = new AddressCepValidator(true, digits -> { throw new java.io.IOException("sem rede"); });
        assertThatCode(() -> offline.validate("04144-000", "Itaim Bibi")).doesNotThrowAnyException();
    }
}
