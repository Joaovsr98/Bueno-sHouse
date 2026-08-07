package com.restaurante.sistema.modules.dinein.service;

import com.restaurante.sistema.common.exception.BusinessException;
import com.restaurante.sistema.common.exception.ResourceNotFoundException;
import com.restaurante.sistema.modules.dinein.domain.RestaurantTable;
import com.restaurante.sistema.modules.dinein.dto.TableRequest;
import com.restaurante.sistema.modules.dinein.dto.TableResponse;
import com.restaurante.sistema.modules.dinein.repository.TableRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Etapa 7. Maquina de estado de Table (mesa), conforme definida na Etapa 2:
 *
 *   LIVRE -> OCUPADA -> AGUARDANDO_LIMPEZA -> LIVRE
 *   OCUPADA -> RESERVADA (fluxo alternativo)
 *   qualquer estado -> BLOQUEADA (acao administrativa) -> LIVRE
 *
 * A transicao LIVRE -> OCUPADA normalmente acontece automaticamente ao abrir
 * um Service (ver ServiceService.open), nao diretamente por este endpoint -
 * mas o metodo changeStatus fica disponivel para ajustes administrativos
 * (ex.: liberar uma mesa apos limpeza, bloquear uma mesa com problema).
 */
@Service
public class TableService {

    private static final java.util.Map<String, Set<String>> ALLOWED_TRANSITIONS = java.util.Map.of(
            "LIVRE", Set.of("OCUPADA", "RESERVADA", "BLOQUEADA"),
            "OCUPADA", Set.of("AGUARDANDO_LIMPEZA", "RESERVADA", "BLOQUEADA"),
            "RESERVADA", Set.of("OCUPADA", "LIVRE", "BLOQUEADA"),
            "AGUARDANDO_LIMPEZA", Set.of("LIVRE", "BLOQUEADA"),
            "BLOQUEADA", Set.of("LIVRE")
    );

    private final TableRepository tableRepository;

    public TableService(TableRepository tableRepository) {
        this.tableRepository = tableRepository;
    }

    @Transactional(readOnly = true)
    public List<TableResponse> listByUnit(Long unitId) {
        return tableRepository.findByUnitId(unitId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public TableResponse create(TableRequest request) {
        RestaurantTable table = new RestaurantTable();
        table.setUnitId(request.unitId());
        table.setNumber(request.number());
        table.setCapacity(request.capacity() != null ? request.capacity() : 4);
        return toResponse(tableRepository.save(table));
    }

    @Transactional
    public TableResponse changeStatus(Long id, String newStatus) {
        RestaurantTable table = getOrThrow(id);
        validateTransition(table.getStatus(), newStatus);
        table.setStatus(newStatus);
        return toResponse(tableRepository.save(table));
    }

    void validateTransition(String from, String to) {
        Set<String> allowed = ALLOWED_TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new BusinessException("Transicao de status invalida para mesa: " + from + " -> " + to);
        }
    }

    RestaurantTable getOrThrow(Long id) {
        return tableRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Table", id));
    }

    private TableResponse toResponse(RestaurantTable t) {
        return new TableResponse(t.getId(), t.getUnitId(), t.getNumber(), t.getCapacity(), t.getStatus());
    }
}
