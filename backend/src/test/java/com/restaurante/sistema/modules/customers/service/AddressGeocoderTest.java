package com.restaurante.sistema.modules.customers.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Escolha da localizacao no servidor, com um Nominatim falso (sem internet). */
class AddressGeocoderTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<String> queries = new ArrayList<>();

    private AddressGeocoder geocoder(Map<String, String> answers) {
        return new AddressGeocoder(query -> {
            queries.add(query);
            return answers.entrySet().stream().filter(e -> query.startsWith(e.getKey()))
                    .map(Map.Entry::getValue).findFirst().orElse("[]");
        }, mapper, 0);
    }

    @Test
    void picksTheCapitalResultWithTheClosestCepNotTheHomonymousStreetInGuarulhos() throws Exception {
        String street = """
                [{"lat":"-23.40","lon":"-46.39","address":{"city":"Guarulhos","postcode":"07175-130"}},
                 {"lat":"-23.70","lon":"-46.77","address":{"city":"São Paulo","postcode":"04800-000"}},
                 {"lat":"-23.69","lon":"-46.76","address":{"city":"São Paulo","postcode":"04940-070"}}]""";

        Optional<AddressGeocoder.Point> point = geocoder(Map.of("street=", street))
                .locate("Rua Bocaina de Minas", "10", "Jardim Angela", "04939-200");

        assertThat(point).contains(new AddressGeocoder.Point(new BigDecimal("-23.690000"), new BigDecimal("-46.760000")));
        assertThat(queries).hasSize(1);
    }

    @Test
    void streetNotFoundFallsBackToTheCepAndThenToTheNeighborhood() throws Exception {
        String neighborhood = """
                [{"lat":"-23.6195","lon":"-46.6394","address":{"city":"São Paulo"}}]""";

        Optional<AddressGeocoder.Point> point = geocoder(Map.of("q=", neighborhood))
                .locate("saude", "447", "Saúde", "06786-075");

        assertThat(point).contains(new AddressGeocoder.Point(new BigDecimal("-23.619500"), new BigDecimal("-46.639400")));
        assertThat(queries).hasSize(3);
        assertThat(queries.get(0)).startsWith("street=447+saude");
        assertThat(queries.get(1)).startsWith("postalcode=06786-075");
    }

    @Test
    void aNeighborhoodOnlyFoundInAnotherCityIsNotAccepted() throws Exception {
        String guarulhos = """
                [{"lat":"-23.4628","lon":"-46.5333","address":{"city":"Guarulhos","postcode":"07010-000"}}]""";

        assertThat(geocoder(Map.of("q=", guarulhos)).locate(null, null, "Centro", null)).isEmpty();
    }

    @Test
    void nothingFoundIsEmptyAndServiceDownIsAnError() throws Exception {
        assertThat(geocoder(Map.of()).locate("Rua Inexistente", "1", "Lugar Nenhum", "00000-000")).isEmpty();

        AddressGeocoder down = new AddressGeocoder(query -> { throw new java.io.IOException("timeout"); }, mapper, 0);
        assertThatThrownBy(() -> down.locate("Rua A", "1", "Centro", "01000-000")).isInstanceOf(java.io.IOException.class);
    }
}
