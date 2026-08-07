package com.restaurante.sistema.modules.organization.service;

import com.restaurante.sistema.common.exception.ResourceNotFoundException;
import com.restaurante.sistema.modules.organization.domain.Restaurant;
import com.restaurante.sistema.modules.organization.domain.Unit;
import com.restaurante.sistema.modules.organization.dto.UnitRequest;
import com.restaurante.sistema.modules.organization.dto.UnitResponse;
import com.restaurante.sistema.modules.organization.repository.RestaurantRepository;
import com.restaurante.sistema.modules.organization.repository.UnitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Etapa 6. CRUD de unidades. Decisao aprovada na Etapa 1: multiunidade
 * preparada no banco desde o inicio, mas sem interface administrativa
 * avancada nesta fase - este service cobre apenas o essencial (criar,
 * listar, atualizar, desativar).
 */
@Service
public class UnitService {

    private final UnitRepository unitRepository;
    private final RestaurantRepository restaurantRepository;

    public UnitService(UnitRepository unitRepository, RestaurantRepository restaurantRepository) {
        this.unitRepository = unitRepository;
        this.restaurantRepository = restaurantRepository;
    }

    @Transactional(readOnly = true)
    public List<UnitResponse> listActive() {
        return unitRepository.findByActiveTrue().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public UnitResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    @Transactional
    public UnitResponse create(UnitRequest request) {
        Restaurant restaurant = restaurantRepository.findById(request.restaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", request.restaurantId()));

        Unit unit = new Unit();
        unit.setRestaurant(restaurant);
        applyRequest(unit, request);

        return toResponse(unitRepository.save(unit));
    }

    @Transactional
    public UnitResponse update(Long id, UnitRequest request) {
        Unit unit = getOrThrow(id);

        if (!unit.getRestaurant().getId().equals(request.restaurantId())) {
            Restaurant restaurant = restaurantRepository.findById(request.restaurantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Restaurant", request.restaurantId()));
            unit.setRestaurant(restaurant);
        }

        applyRequest(unit, request);
        return toResponse(unitRepository.save(unit));
    }

    @Transactional
    public void deactivate(Long id) {
        Unit unit = getOrThrow(id);
        unit.setActive(false);
        unitRepository.save(unit);
    }

    private void applyRequest(Unit unit, UnitRequest request) {
        unit.setName(request.name());
        unit.setAddress(request.address());
        unit.setPhone(request.phone());
        if (request.timezone() != null && !request.timezone().isBlank()) {
            unit.setTimezone(request.timezone());
        }
    }

    private Unit getOrThrow(Long id) {
        return unitRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Unit", id));
    }

    private UnitResponse toResponse(Unit unit) {
        return new UnitResponse(
                unit.getId(),
                unit.getRestaurant().getId(),
                unit.getRestaurant().getTradeName(),
                unit.getName(),
                unit.getAddress(),
                unit.getPhone(),
                unit.getTimezone(),
                unit.isActive()
        );
    }
}
