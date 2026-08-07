package com.restaurante.sistema.modules.organization.controller;

import com.restaurante.sistema.modules.organization.dto.UnitRequest;
import com.restaurante.sistema.modules.organization.dto.UnitResponse;
import com.restaurante.sistema.modules.organization.service.UnitService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Apenas ADMINISTRADOR gerencia unidades (criacao/edicao de estabelecimentos
 * e um dado sensivel de configuracao, nao operacional do dia a dia).
 * Qualquer usuario autenticado pode listar/consultar.
 */
@RestController
@RequestMapping("/api/units")
public class UnitController {

    private final UnitService unitService;

    public UnitController(UnitService unitService) {
        this.unitService = unitService;
    }

    @GetMapping
    public List<UnitResponse> list() {
        return unitService.listActive();
    }

    @GetMapping("/{id}")
    public UnitResponse findById(@PathVariable Long id) {
        return unitService.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public UnitResponse create(@Valid @RequestBody UnitRequest request) {
        return unitService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public UnitResponse update(@PathVariable Long id, @Valid @RequestBody UnitRequest request) {
        return unitService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        unitService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
