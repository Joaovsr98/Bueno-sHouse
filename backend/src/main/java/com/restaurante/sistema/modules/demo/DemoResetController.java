package com.restaurante.sistema.modules.demo;

import com.restaurante.sistema.common.exception.BusinessException;
import com.restaurante.sistema.common.exception.ConcurrencyConflictException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class DemoResetController {

    private final DemoResetService service;

    public DemoResetController(DemoResetService service) {
        this.service = service;
    }

    /** So o administrador, digitando RESTAURAR (evita restaurar com um clique sem querer). */
    @PostMapping("/api/admin/demo/reset")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Map<String, String>> reset(@RequestBody(required = false) Map<String, String> body) {
        if (body == null || !"RESTAURAR".equals(body.get("confirm"))) {
            throw new BusinessException("Para confirmar, digite RESTAURAR");
        }
        if (!service.start()) {
            throw new ConcurrencyConflictException("Ja existe uma restauracao em andamento");
        }
        return ResponseEntity.accepted().body(Map.of(
                "message", "Restaurando a demonstracao. Leva cerca de um minuto; depois entre de novo."));
    }

    /** Publico: durante a restauracao a tabela de usuarios nem existe, entao nao ha como logar. */
    @GetMapping("/api/demo/reset-status")
    public DemoResetService.Status status() {
        return service.status();
    }
}
