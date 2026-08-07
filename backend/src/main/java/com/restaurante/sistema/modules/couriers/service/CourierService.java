package com.restaurante.sistema.modules.couriers.service;

import com.restaurante.sistema.common.exception.BusinessException;
import com.restaurante.sistema.common.exception.ResourceNotFoundException;
import com.restaurante.sistema.modules.couriers.domain.Courier;
import com.restaurante.sistema.modules.couriers.dto.CourierRequest;
import com.restaurante.sistema.modules.couriers.dto.CourierResponse;
import com.restaurante.sistema.modules.couriers.repository.CourierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Etapa 10 (Fase 1.5) - modulo couriers.
 *
 * Maquina de estado (Etapa 2):
 *   OFFLINE ⇄ DISPONIVEL ⇄ OCUPADO ⇄ EM_ENTREGA
 *   qualquer estado -> PAUSADO | BLOQUEADO (acao administrativa)
 */
@Service
public class CourierService {

    private static final java.util.Map<String, Set<String>> ALLOWED_TRANSITIONS = java.util.Map.of(
            "OFFLINE", Set.of("DISPONIVEL", "PAUSADO", "BLOQUEADO"),
            "DISPONIVEL", Set.of("OCUPADO", "OFFLINE", "PAUSADO", "BLOQUEADO"),
            "OCUPADO", Set.of("EM_ENTREGA", "DISPONIVEL", "PAUSADO", "BLOQUEADO"),
            "EM_ENTREGA", Set.of("DISPONIVEL", "OCUPADO", "PAUSADO", "BLOQUEADO"),
            "PAUSADO", Set.of("OFFLINE", "DISPONIVEL", "BLOQUEADO"),
            "BLOQUEADO", Set.of("OFFLINE")
    );

    private final CourierRepository courierRepository;

    public CourierService(CourierRepository courierRepository) {
        this.courierRepository = courierRepository;
    }

    @Transactional(readOnly = true)
    public List<CourierResponse> listAvailable(Long unitId) {
        return courierRepository.findByUnitIdAndStatus(unitId, "DISPONIVEL").stream().map(this::toResponse).toList();
    }

    @Transactional
    public CourierResponse create(CourierRequest request) {
        if (courierRepository.findByUserId(request.userId()).isPresent()) {
            throw new BusinessException("Ja existe um entregador cadastrado para este usuario");
        }

        Courier courier = new Courier();
        courier.setUnitId(request.unitId());
        courier.setUserId(request.userId());
        courier.setFullName(request.fullName());
        courier.setPhone(request.phone());
        courier.setDocument(request.document());
        courier.setVehicleType(request.vehicleType());
        courier.setPlate(request.plate());

        return toResponse(courierRepository.save(courier));
    }

    @Transactional
    public CourierResponse changeStatus(Long id, String newStatus) {
        Courier courier = getOrThrow(id);
        validateTransition(courier.getStatus(), newStatus);
        courier.setStatus(newStatus);
        return toResponse(courierRepository.save(courier));
    }

    private void validateTransition(String from, String to) {
        Set<String> allowed = ALLOWED_TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new BusinessException("Transicao de status invalida para entregador: " + from + " -> " + to);
        }
    }

    Courier getOrThrow(Long id) {
        return courierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Courier", id));
    }

    private CourierResponse toResponse(Courier c) {
        return new CourierResponse(c.getId(), c.getUnitId(), c.getFullName(), c.getPhone(), c.getVehicleType(), c.getPlate(), c.getStatus());
    }
}
