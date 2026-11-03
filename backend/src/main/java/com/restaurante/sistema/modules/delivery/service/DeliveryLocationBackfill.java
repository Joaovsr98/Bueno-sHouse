package com.restaurante.sistema.modules.delivery.service;

import com.restaurante.sistema.modules.customers.service.AddressGeocoder;
import com.restaurante.sistema.modules.delivery.domain.Delivery;
import com.restaurante.sistema.modules.delivery.repository.DeliveryRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Localiza as entregas em andamento que ainda estao sem latitude/longitude (criadas antes de o
 * servidor guardar a localizacao, ou de um endereco cuja busca ainda nao tinha terminado). Sem
 * isso o site buscava so pelo bairro, e "Centro" virava um Centro a 20 km da cozinha.
 * Usa o endereco completo gravado na entrega; roda ao subir e a cada 5 minutos.
 */
@Component
public class DeliveryLocationBackfill {

    private static final Logger log = LoggerFactory.getLogger(DeliveryLocationBackfill.class);

    /** "Rua das Flores, 100 (apto 2) - Centro, Sao Paulo/SP - CEP 01000-000" */
    private static final Pattern SNAPSHOT = Pattern.compile(
            "^(.*?),\s*([^\s(]+)(?:\s*\([^)]*\))?\s*-\s*(.*?),\s*.*?/\w{2}\s*-\s*CEP\s*(\d{5}-?\d{3})");

    private static final List<String> ACTIVE = List.of(
            "AGUARDANDO_ENTREGADOR", "ACEITA", "RETIRADA_NO_RESTAURANTE", "EM_ROTA", "NAO_ENTREGUE");

    private final boolean enabled;
    private final AddressGeocoder geocoder;
    private final DeliveryRepository deliveryRepository;
    private final ScheduledExecutorService queue = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "localizacao-entregas");
        thread.setDaemon(true);
        return thread;
    });

    public DeliveryLocationBackfill(@Value("${app.geocoding.enabled:true}") boolean enabled,
                                    AddressGeocoder geocoder, DeliveryRepository deliveryRepository) {
        this.enabled = enabled;
        this.geocoder = geocoder;
        this.deliveryRepository = deliveryRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!enabled) return;
        queue.scheduleWithFixedDelay(this::run, 20, 300, TimeUnit.SECONDS);
    }

    private void run() {
        try {
            for (Delivery d : deliveryRepository.findTop20ByLatitudeIsNullAndStatusInOrderByIdAsc(ACTIVE)) {
                Matcher m = SNAPSHOT.matcher(d.getAddressSnapshot());
                var point = m.find()
                        ? geocoder.locate(m.group(1).trim(), m.group(2), m.group(3).trim(), m.group(4))
                        : geocoder.locate(null, null, d.getNeighborhoodSnapshot(), null);
                point.ifPresent(p -> deliveryRepository.saveLocation(d.getId(), p.latitude(), p.longitude()));
            }
        } catch (Exception e) {
            // OpenStreetMap fora do ar ou banco ocupado: tenta na proxima rodada.
            log.warn("Falha ao localizar entregas antigas: {}", e.toString());
        }
    }

    @PreDestroy
    void stop() {
        queue.shutdownNow();
    }
}
