package com.restaurante.sistema.modules.catalog.controller;

import com.restaurante.sistema.modules.catalog.dto.AdditionalGroupRequest;
import com.restaurante.sistema.modules.catalog.dto.AdditionalGroupResponse;
import com.restaurante.sistema.modules.catalog.dto.AdditionalRequest;
import com.restaurante.sistema.modules.catalog.service.AdditionalGroupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/additional-groups")
public class AdditionalGroupController {

    private final AdditionalGroupService additionalGroupService;

    public AdditionalGroupController(AdditionalGroupService additionalGroupService) {
        this.additionalGroupService = additionalGroupService;
    }

    @GetMapping
    public List<AdditionalGroupResponse> list(@RequestParam Long unitId) {
        return additionalGroupService.listByUnit(unitId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    @ResponseStatus(HttpStatus.CREATED)
    public AdditionalGroupResponse create(@Valid @RequestBody AdditionalGroupRequest request) {
        return additionalGroupService.create(request);
    }

    @PostMapping("/{groupId}/additionals")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    @ResponseStatus(HttpStatus.CREATED)
    public AdditionalGroupResponse addAdditional(
            @PathVariable Long groupId,
            @Valid @RequestBody AdditionalRequest request
    ) {
        return additionalGroupService.addAdditional(groupId, request);
    }
}
