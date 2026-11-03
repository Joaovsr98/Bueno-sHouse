package com.restaurante.sistema.modules.customers.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.sistema.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.Normalizer;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Confere no servidor o que o site ja confere no navegador: o CEP precisa existir, ser
 * da cidade de Sao Paulo e pertencer ao bairro informado. Sem isso, quem chamasse a API
 * direto cadastraria um CEP de Taboao da Serra no bairro Itaim e pagaria a taxa errada.
 *
 * Consulta o ViaCEP (servico publico dos Correios). Se ele estiver fora do ar, o endereco
 * e aceito (so o formato ja foi validado) para o cliente nao ficar sem pedir.
 */
@Component
public class AddressCepValidator {

    private static final Logger log = LoggerFactory.getLogger(AddressCepValidator.class);

    /**
     * Nomes do ViaCEP que, na lista de entrega, aparecem com outro nome
     * (os Correios chamam o Centro de "Se"; "Jardins" junta varios Jardins).
     */
    static final Map<String, String> ALIASES = Map.of(
            "se", "centro",
            "luz", "centro",
            "santa efigenia", "centro",
            "jardim paulista", "jardins",
            "jardim america", "jardins",
            "jardim europa", "jardins",
            "cerqueira cesar", "jardins");

    /** O que o ViaCEP diz sobre um CEP. */
    public record CepInfo(String neighborhood, String city) {}

    /** Busca o CEP; vazio = CEP inexistente. Lanca excecao se o servico estiver fora. */
    @FunctionalInterface
    public interface CepDirectory {
        Optional<CepInfo> find(String cepDigits) throws Exception;
    }

    private final boolean enabled;
    private final CepDirectory directory;

    @org.springframework.beans.factory.annotation.Autowired
    public AddressCepValidator(@Value("${app.cep-check.enabled:true}") boolean enabled, ObjectMapper mapper) {
        this(enabled, viaCep(mapper));
    }

    AddressCepValidator(boolean enabled, CepDirectory directory) {
        this.enabled = enabled;
        this.directory = directory;
    }

    public void validate(String zipCode, String neighborhood) {
        if (!enabled) return;
        String digits = zipCode.replaceAll("\\D", "");
        Optional<CepInfo> info;
        try {
            info = directory.find(digits);
        } catch (Exception e) {
            log.warn("ViaCEP indisponivel ao validar o CEP {}: {}", digits, e.toString());
            return;
        }
        if (info.isEmpty()) {
            throw new BusinessException("CEP nao encontrado: " + zipCode);
        }
        CepInfo cep = info.get();
        if (!normalize(cep.city()).equals("sao paulo")) {
            throw new BusinessException("O CEP " + zipCode + " e de " + cep.city()
                    + ". Entregamos so na cidade de Sao Paulo; voce pode retirar no balcao.");
        }
        if (cep.neighborhood() != null && !cep.neighborhood().isBlank() && !matches(cep.neighborhood(), neighborhood)) {
            throw new BusinessException("O CEP " + zipCode + " e do bairro " + cep.neighborhood()
                    + ", nao de " + neighborhood + ".");
        }
    }

    /** Mesmo nome, um contido no outro ("Vila da Saude" / "Saude") ou apelido conhecido. */
    static boolean matches(String cepNeighborhood, String declared) {
        String a = normalize(cepNeighborhood);
        String b = normalize(declared);
        if (a.equals(b) || b.equals(ALIASES.get(a))) return true;
        return a.length() >= 4 && b.length() >= 4 && (a.contains(b) || b.contains(a));
    }

    static String normalize(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().trim();
    }

    private static CepDirectory viaCep(ObjectMapper mapper) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        return digits -> {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://viacep.com.br/ws/" + digits + "/json/"))
                    .timeout(Duration.ofSeconds(4)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 400) return Optional.empty();
            if (response.statusCode() != 200) throw new IllegalStateException("ViaCEP HTTP " + response.statusCode());
            JsonNode json = mapper.readTree(response.body());
            if (json.path("erro").asBoolean(false) || "true".equals(json.path("erro").asText())) return Optional.empty();
            return Optional.of(new CepInfo(json.path("bairro").asText(""), json.path("localidade").asText("")));
        };
    }
}
