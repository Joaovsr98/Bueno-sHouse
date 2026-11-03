package com.restaurante.sistema.modules.customers.service;

import com.restaurante.sistema.modules.customers.domain.CustomerAddress;
import com.restaurante.sistema.modules.customers.repository.CustomerAddressRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Busca a localizacao dos enderecos fora da requisicao do cliente: o cadastro responde na
 * hora e, depois do commit, uma fila de uma busca por vez (regra do Nominatim) grava
 * latitude/longitude. Enderecos antigos, ainda sem localizacao, sao buscados aos poucos (ao
 * subir o servidor e a cada 30 minutos). Desligado nos testes (app.geocoding.enabled).
 */
@Service
public class AddressLocationService {

    private static final Logger log = LoggerFactory.getLogger(AddressLocationService.class);

    private final boolean enabled;
    private final AddressGeocoder geocoder;
    private final CustomerAddressRepository addressRepository;
    private final ScheduledExecutorService queue = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "localizacao-enderecos");
        thread.setDaemon(true);
        return thread;
    });

    public AddressLocationService(@Value("${app.geocoding.enabled:true}") boolean enabled,
                                  AddressGeocoder geocoder, CustomerAddressRepository addressRepository) {
        this.enabled = enabled;
        this.geocoder = geocoder;
        this.addressRepository = addressRepository;
    }

    /** Agenda a busca para depois do commit do cadastro (antes disso a fila nem enxerga o endereco). */
    public void locateAfterCommit(Long addressId) {
        if (!enabled) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    queue.execute(() -> locate(addressId));
                }
            });
        } else {
            queue.execute(() -> locate(addressId));
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startBackfill() {
        if (!enabled) return;
        queue.scheduleWithFixedDelay(this::backfill, 1, 30, TimeUnit.MINUTES);
    }

    private void backfill() {
        try {
            for (CustomerAddress address : addressRepository.findTop20ByGeocodedAtIsNullOrderByIdAsc()) {
                locate(address.getId());
            }
        } catch (RuntimeException e) {
            log.warn("Falha ao localizar enderecos antigos: {}", e.toString());
        }
    }

    void locate(Long addressId) {
        addressRepository.findById(addressId).ifPresent(address -> {
            try {
                Optional<AddressGeocoder.Point> point = geocoder.locate(
                        address.getStreet(), address.getNumber(), address.getNeighborhood(), address.getZipCode());
                addressRepository.saveLocation(address.getId(),
                        point.map(AddressGeocoder.Point::latitude).orElse(null),
                        point.map(AddressGeocoder.Point::longitude).orElse(null),
                        Instant.now());
            } catch (Exception e) {
                // Fora do ar: fica sem localizacao e entra na proxima rodada (o site busca no celular enquanto isso).
                log.warn("Nao foi possivel localizar o endereco {} agora: {}", address.getId(), e.toString());
            }
        });
    }

    @PreDestroy
    void stop() {
        queue.shutdownNow();
    }
}
