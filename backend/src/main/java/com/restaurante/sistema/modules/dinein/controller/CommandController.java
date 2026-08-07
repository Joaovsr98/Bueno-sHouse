package com.restaurante.sistema.modules.dinein.controller;

import com.restaurante.sistema.modules.dinein.dto.CommandResponse;
import com.restaurante.sistema.modules.dinein.dto.TransitionRequest;
import com.restaurante.sistema.modules.dinein.service.CommandService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/commands")
public class CommandController {

    private final CommandService commandService;

    public CommandController(CommandService commandService) {
        this.commandService = commandService;
    }

    @GetMapping
    public List<CommandResponse> listByService(@RequestParam Long serviceId) {
        return commandService.listByService(serviceId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE','GARCOM')")
    @ResponseStatus(HttpStatus.CREATED)
    public CommandResponse open(@RequestParam Long serviceId) {
        return commandService.open(serviceId);
    }

    @PostMapping("/{id}/transitions")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE','GARCOM','CAIXA')")
    public CommandResponse transition(@PathVariable Long id, @Valid @RequestBody TransitionRequest request) {
        return commandService.transitionTo(id, request.status());
    }
}
