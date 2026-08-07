package com.restaurante.sistema.modules.dinein.controller;

import com.restaurante.sistema.modules.dinein.dto.TableRequest;
import com.restaurante.sistema.modules.dinein.dto.TableResponse;
import com.restaurante.sistema.modules.dinein.dto.TransitionRequest;
import com.restaurante.sistema.modules.dinein.service.TableService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tables")
public class TableController {

    private final TableService tableService;

    public TableController(TableService tableService) {
        this.tableService = tableService;
    }

    @GetMapping
    public List<TableResponse> list(@RequestParam Long unitId) {
        return tableService.listByUnit(unitId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    @ResponseStatus(HttpStatus.CREATED)
    public TableResponse create(@Valid @RequestBody TableRequest request) {
        return tableService.create(request);
    }

    /**
     * Transicao manual de status (ex.: liberar mesa apos limpeza, bloquear
     * mesa com problema). A transicao LIVRE -> OCUPADA normalmente acontece
     * automaticamente ao abrir um atendimento (ver ServiceController).
     */
    @PostMapping("/{id}/transitions")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE','GARCOM')")
    public TableResponse changeStatus(@PathVariable Long id, @Valid @RequestBody TransitionRequest request) {
        return tableService.changeStatus(id, request.status());
    }
}
