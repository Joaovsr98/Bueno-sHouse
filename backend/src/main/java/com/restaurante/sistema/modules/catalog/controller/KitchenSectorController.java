package com.restaurante.sistema.modules.catalog.controller;

import com.restaurante.sistema.modules.catalog.domain.KitchenSector;
import com.restaurante.sistema.modules.catalog.dto.KitchenSectorResponse;
import com.restaurante.sistema.modules.catalog.repository.KitchenSectorRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoint minimo de leitura - o modulo kitchen completo (painel em tempo
 * real) mora em modules/kitchen; esta entidade fica em catalog porque
 * "products" ja referenciava setor desde a Etapa 6.
 */
@RestController
@RequestMapping("/api/kitchen-sectors")
public class KitchenSectorController {

    private final KitchenSectorRepository repository;

    public KitchenSectorController(KitchenSectorRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<KitchenSectorResponse> list() {
        return repository.findAll().stream()
                .map(s -> new KitchenSectorResponse(s.getId(), s.getUnitId(), s.getName()))
                .toList();
    }
}
