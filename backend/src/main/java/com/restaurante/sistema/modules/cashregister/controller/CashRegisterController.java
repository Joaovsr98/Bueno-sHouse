package com.restaurante.sistema.modules.cashregister.controller;

import com.restaurante.sistema.modules.cashregister.dto.*;
import com.restaurante.sistema.modules.cashregister.service.CashRegisterService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Operado pelo perfil CAIXA (abrir/fechar caixa, sangria, suprimento).
 * GERENTE/ADMINISTRADOR tambem tem acesso para supervisao.
 */
@RestController
@RequestMapping("/api/cash-registers")
@PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE','CAIXA')")
public class CashRegisterController {

    private final CashRegisterService cashRegisterService;

    public CashRegisterController(CashRegisterService cashRegisterService) {
        this.cashRegisterService = cashRegisterService;
    }

    @GetMapping("/current")
    public CashRegisterResponse current(@RequestParam Long unitId) {
        return cashRegisterService.findCurrentOpen(unitId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CashRegisterResponse open(@Valid @RequestBody OpenCashRegisterRequest request) {
        return cashRegisterService.open(request);
    }

    @PostMapping("/{id}/close")
    public CashRegisterResponse close(@PathVariable Long id, @Valid @RequestBody CloseCashRegisterRequest request) {
        return cashRegisterService.close(id, request);
    }

    @PostMapping("/{id}/movements")
    @ResponseStatus(HttpStatus.CREATED)
    public CashMovementResponse registerMovement(
            @PathVariable Long id,
            @Valid @RequestBody CashMovementRequest request
    ) {
        return cashRegisterService.registerMovement(id, request);
    }

    @GetMapping("/{id}/movements")
    public List<CashMovementResponse> listMovements(@PathVariable Long id) {
        return cashRegisterService.listMovements(id);
    }
}
