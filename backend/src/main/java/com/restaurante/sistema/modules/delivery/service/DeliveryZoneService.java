package com.restaurante.sistema.modules.delivery.service;

import com.restaurante.sistema.modules.delivery.domain.DeliveryZone;
import com.restaurante.sistema.modules.delivery.dto.DeliveryZoneRequest;
import com.restaurante.sistema.modules.delivery.dto.DeliveryZoneResponse;
import com.restaurante.sistema.modules.delivery.repository.DeliveryZoneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class DeliveryZoneService {

    private final DeliveryZoneRepository repository;

    public DeliveryZoneService(DeliveryZoneRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<DeliveryZoneResponse> listByUnit(Long unitId) {
        return repository.findByUnitIdAndActiveTrue(unitId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public DeliveryZoneResponse create(DeliveryZoneRequest request) {
        DeliveryZone zone = new DeliveryZone();
        zone.setUnitId(request.unitId());
        zone.setZoneName(request.zoneName());
        zone.setNeighborhood(request.neighborhood());
        zone.setFee(request.fee());
        zone.setMinimumOrderValue(request.minimumOrderValue() != null ? request.minimumOrderValue() : BigDecimal.ZERO);
        zone.setEstimatedMinutes(request.estimatedMinutes());
        return toResponse(repository.save(zone));
    }

    private DeliveryZoneResponse toResponse(DeliveryZone z) {
        return new DeliveryZoneResponse(
                z.getId(), z.getUnitId(), z.getZoneName(), z.getNeighborhood(), z.getFee(),
                z.getMinimumOrderValue(), z.getEstimatedMinutes(), z.isActive()
        );
    }
}
