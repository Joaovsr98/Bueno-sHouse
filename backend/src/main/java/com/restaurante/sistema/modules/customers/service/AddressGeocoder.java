package com.restaurante.sistema.modules.customers.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Localizacao (latitude/longitude) de um endereco da cidade de Sao Paulo pelo OpenStreetMap
 * (Nominatim, gratuito e sem chave). E a mesma busca que o site fazia no celular do motoboy:
 * rua e numero, depois o CEP e por fim o bairro, presa ao retangulo da capital ("city=Sao
 * Paulo" sozinho tambem casa com o estado e devolvia ruas de mesmo nome em Guarulhos); entre
 * os resultados, fica com o da capital cujo CEP mais se parece com o informado.
 *
 * O Nominatim pede no maximo 1 consulta por segundo e um User-Agent que identifique o
 * sistema: as consultas daqui respeitam o intervalo, e quem chama (AddressLocationService)
 * busca um endereco de cada vez, fora da requisicao do cliente.
 */
@Component
public class AddressGeocoder {

    /** Ponto no mapa, com as 6 casas decimais que a coluna guarda. */
    public record Point(BigDecimal latitude, BigDecimal longitude) {}

    /** Uma consulta ao Nominatim: recebe os parametros da busca e devolve o JSON da resposta. */
    @FunctionalInterface
    public interface Search {
        String run(String query) throws Exception;
    }

    /** Retangulo da cidade de Sao Paulo (oeste, norte, leste, sul), o mesmo do site. */
    private static final String VIEWBOX = "-46.83,-23.35,-46.36,-24.01";

    private final Search search;
    private final ObjectMapper mapper;
    private final long minIntervalMs;
    private long lastCall = 0;

    @Autowired
    public AddressGeocoder(ObjectMapper mapper) {
        this(nominatim(), mapper, 1100);
    }

    AddressGeocoder(Search search, ObjectMapper mapper, long minIntervalMs) {
        this.search = search;
        this.mapper = mapper;
        this.minIntervalMs = minIntervalMs;
    }

    /**
     * Vazio = o endereco nao foi encontrado. Lanca excecao se o servico estiver fora do ar
     * (quem chama tenta de novo mais tarde).
     */
    public Optional<Point> locate(String street, String number, String neighborhood, String zipCode) throws Exception {
        List<String> attempts = new ArrayList<>();
        if (notBlank(street)) {
            String streetAndNumber = ((number == null ? "" : number) + " " + street).trim();
            attempts.add("street=" + enc(streetAndNumber) + "&city=" + enc("São Paulo") + "&country=Brasil");
        }
        if (notBlank(zipCode)) attempts.add("postalcode=" + enc(zipCode) + "&country=Brasil");
        if (notBlank(neighborhood)) attempts.add("q=" + enc(neighborhood + ", São Paulo, SP"));

        for (String query : attempts) {
            Optional<Point> found = best(read(query), zipCode);
            if (found.isPresent()) return found;
        }
        return Optional.empty();
    }

    /** Entre os resultados, os da capital (se houver) e, deles, o de CEP mais parecido. */
    static Optional<Point> best(JsonNode results, String zipCode) {
        List<JsonNode> all = new ArrayList<>();
        results.forEach(all::add);
        List<JsonNode> inCity = all.stream().filter(p -> "São Paulo".equals(cityOf(p))).toList();
        List<JsonNode> candidates = inCity.isEmpty() ? all : inCity;
        // max fica com o primeiro em caso de empate (a ordem de relevancia do Nominatim)
        return candidates.stream()
                .max(Comparator.comparingInt(p -> cepScore(p, zipCode)))
                .map(p -> new Point(coordinate(p.path("lat")), coordinate(p.path("lon"))));
    }

    /** Quantos digitos iniciais do CEP batem: ruas homonimas na mesma cidade tem CEPs diferentes. */
    static int cepScore(JsonNode place, String zipCode) {
        String want = zipCode == null ? "" : zipCode.replaceAll("\\D", "");
        String got = place.path("address").path("postcode").asText("").replaceAll("\\D", "");
        int n = 0;
        while (n < want.length() && n < got.length() && want.charAt(n) == got.charAt(n)) n++;
        return n;
    }

    private static String cityOf(JsonNode place) {
        JsonNode address = place.path("address");
        for (String field : List.of("city", "municipality", "town")) {
            if (address.hasNonNull(field)) return address.get(field).asText();
        }
        return "";
    }

    private static BigDecimal coordinate(JsonNode value) {
        return new BigDecimal(value.asText()).setScale(6, RoundingMode.HALF_UP);
    }

    /** Uma consulta por vez, com o intervalo minimo entre elas. */
    private synchronized JsonNode read(String query) throws Exception {
        long wait = lastCall + minIntervalMs - System.currentTimeMillis();
        if (wait > 0) Thread.sleep(wait);
        try {
            return mapper.readTree(search.run(query));
        } finally {
            lastCall = System.currentTimeMillis();
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static Search nominatim() {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
        return query -> {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            "https://nominatim.openstreetmap.org/search?format=json&limit=10&addressdetails=1"
                                    + "&viewbox=" + VIEWBOX + "&bounded=1&" + query))
                    .header("User-Agent", "BuenosHouse/1.0 (+https://github.com/Joaovsr98/Bueno-sHouse)")
                    .header("Accept-Language", "pt-BR")
                    .timeout(Duration.ofSeconds(6)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("Nominatim HTTP " + response.statusCode());
            return response.body();
        };
    }
}
