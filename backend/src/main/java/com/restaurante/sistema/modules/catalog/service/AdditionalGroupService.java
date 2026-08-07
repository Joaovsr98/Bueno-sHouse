package com.restaurante.sistema.modules.catalog.service;

import com.restaurante.sistema.common.exception.BusinessException;
import com.restaurante.sistema.common.exception.ResourceNotFoundException;
import com.restaurante.sistema.modules.catalog.domain.Additional;
import com.restaurante.sistema.modules.catalog.domain.AdditionalGroup;
import com.restaurante.sistema.modules.catalog.dto.*;
import com.restaurante.sistema.modules.catalog.repository.AdditionalGroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdditionalGroupService {

    private final AdditionalGroupRepository additionalGroupRepository;

    public AdditionalGroupService(AdditionalGroupRepository additionalGroupRepository) {
        this.additionalGroupRepository = additionalGroupRepository;
    }

    @Transactional(readOnly = true)
    public List<AdditionalGroupResponse> listByUnit(Long unitId) {
        return additionalGroupRepository.findByUnitId(unitId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public AdditionalGroupResponse create(AdditionalGroupRequest request) {
        AdditionalGroup group = new AdditionalGroup();
        group.setUnitId(request.unitId());
        applyRequest(group, request);
        return toResponse(additionalGroupRepository.save(group));
    }

    @Transactional
    public AdditionalGroupResponse addAdditional(Long groupId, AdditionalRequest request) {
        AdditionalGroup group = getOrThrow(groupId);
        Additional additional = new Additional();
        additional.setAdditionalGroup(group);
        additional.setName(request.name());
        additional.setPrice(request.price());
        group.getAdditionals().add(additional);
        return toResponse(additionalGroupRepository.save(group));
    }

    private void applyRequest(AdditionalGroup group, AdditionalGroupRequest request) {
        int min = request.minQuantity() != null ? request.minQuantity() : 0;
        int max = request.maxQuantity() != null ? request.maxQuantity() : 1;

        if (max < min) {
            throw new BusinessException("A quantidade maxima nao pode ser menor que a minima");
        }

        group.setName(request.name());
        group.setMinQuantity(min);
        group.setMaxQuantity(max);
        group.setRequired(request.required() != null && request.required());
    }

    private AdditionalGroup getOrThrow(Long id) {
        return additionalGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AdditionalGroup", id));
    }

    private AdditionalGroupResponse toResponse(AdditionalGroup g) {
        List<AdditionalResponse> additionals = g.getAdditionals().stream()
                .map(a -> new AdditionalResponse(a.getId(), a.getName(), a.getPrice(), a.isActive()))
                .toList();

        return new AdditionalGroupResponse(
                g.getId(), g.getUnitId(), g.getName(), g.getMinQuantity(), g.getMaxQuantity(), g.isRequired(), additionals
        );
    }
}
