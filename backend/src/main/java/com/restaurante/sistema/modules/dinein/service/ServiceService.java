package com.restaurante.sistema.modules.dinein.service;

import com.restaurante.sistema.common.exception.BusinessException;
import com.restaurante.sistema.common.exception.ResourceNotFoundException;
import com.restaurante.sistema.modules.dinein.domain.RestaurantTable;
import com.restaurante.sistema.modules.dinein.dto.OpenServiceRequest;
import com.restaurante.sistema.modules.dinein.dto.ServiceResponse;
import com.restaurante.sistema.modules.dinein.repository.CommandRepository;
import com.restaurante.sistema.modules.dinein.repository.ServiceRepository;
import com.restaurante.sistema.modules.identity.security.CurrentUserProvider;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Etapa 7. Gerencia o Atendimento (Service), a entidade adicionada apos a
 * revisao de arquitetura aprovada antes da Etapa 3.
 *
 * Maquina de estado (Etapa 2):
 *   OPEN -> IN_SERVICE -> AWAITING_CLOSURE -> CLOSED
 *   qualquer estado nao-CLOSED -> CANCELLED (com justificativa)
 *
 * Invariantes aplicadas aqui (alem do indice unico do banco, que e a ultima
 * linha de defesa contra concorrencia):
 *   - uma mesa nao pode ter dois atendimentos ativos (validado tambem na
 *     aplicacao antes de tentar o INSERT, para dar uma mensagem de erro
 *     legivel em vez de estourar a constraint do banco);
 *   - encerrar um Service exige que todas as Command vinculadas estejam
 *     FECHADA ou CANCELADA (Command ainda nao tem regras de pagamento -
 *     isso vem no modulo payments/ordering).
 */
@org.springframework.stereotype.Service
public class ServiceService {

    private static final java.util.Map<String, Set<String>> ALLOWED_TRANSITIONS = java.util.Map.of(
            "OPEN", Set.of("IN_SERVICE", "AWAITING_CLOSURE", "CANCELLED"),
            "IN_SERVICE", Set.of("AWAITING_CLOSURE", "CANCELLED"),
            "AWAITING_CLOSURE", Set.of("CLOSED", "IN_SERVICE", "CANCELLED"),
            "CLOSED", Set.of(),
            "CANCELLED", Set.of()
    );

    private static final Set<String> COMMAND_TERMINAL_STATUSES = Set.of("FECHADA", "CANCELADA");

    private final ServiceRepository serviceRepository;
    private final CommandRepository commandRepository;
    private final TableService tableService;
    private final CurrentUserProvider currentUserProvider;

    public ServiceService(
            ServiceRepository serviceRepository,
            CommandRepository commandRepository,
            TableService tableService,
            CurrentUserProvider currentUserProvider
    ) {
        this.serviceRepository = serviceRepository;
        this.commandRepository = commandRepository;
        this.tableService = tableService;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public List<ServiceResponse> history(Long tableId) {
        return serviceRepository.findByTableIdOrderByOpenedAtDesc(tableId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ServiceResponse open(OpenServiceRequest request) {
        RestaurantTable table = tableService.getOrThrow(request.tableId());

        boolean alreadyActive = serviceRepository.findByTableIdAndActiveFlag(table.getId(), 1).isPresent();
        if (alreadyActive) {
            throw new BusinessException("Esta mesa ja possui um atendimento ativo");
        }

        com.restaurante.sistema.modules.dinein.domain.Service service =
                new com.restaurante.sistema.modules.dinein.domain.Service();
        service.setUnitId(table.getUnitId());
        service.setTable(table);
        service.setOpenedBy(currentUserProvider.getCurrentUserId());
        service.setPartySize(request.partySize() != null ? request.partySize() : 1);
        service.setNotes(request.notes());
        service.setStatus("OPEN");
        service.setOpenedAt(Instant.now());

        com.restaurante.sistema.modules.dinein.domain.Service saved = serviceRepository.save(service);

        // Transicao da mesa: LIVRE/RESERVADA -> OCUPADA
        if ("LIVRE".equals(table.getStatus()) || "RESERVADA".equals(table.getStatus())) {
            tableService.changeStatus(table.getId(), "OCUPADA");
        } else {
            throw new BusinessException(
                    "Mesa nao esta disponivel para abrir atendimento (status atual: " + table.getStatus() + ")");
        }

        return toResponse(saved);
    }

    @Transactional
    public ServiceResponse transitionTo(Long serviceId, String newStatus) {
        com.restaurante.sistema.modules.dinein.domain.Service service = getOrThrow(serviceId);
        validateTransition(service.getStatus(), newStatus);

        if ("CLOSED".equals(newStatus)) {
            ensureAllCommandsClosed(service.getId());
            service.setClosedAt(Instant.now());
            // Mesa vai para limpeza, nao direto para LIVRE (regra do fluxo original)
            tableService.changeStatus(service.getTable().getId(), "AGUARDANDO_LIMPEZA");
        }

        service.setStatus(newStatus);
        return toResponse(serviceRepository.save(service));
    }

    @Transactional
    public ServiceResponse cancel(Long serviceId, String reason) {
        com.restaurante.sistema.modules.dinein.domain.Service service = getOrThrow(serviceId);
        validateTransition(service.getStatus(), "CANCELLED");

        service.setStatus("CANCELLED");
        service.setCancelReason(reason);
        service.setClosedAt(Instant.now());

        // Libera a mesa direto (nao ha o que limpar de um atendimento cancelado sem consumo)
        tableService.changeStatus(service.getTable().getId(), "AGUARDANDO_LIMPEZA");

        return toResponse(serviceRepository.save(service));
    }

    private void ensureAllCommandsClosed(Long serviceId) {
        boolean hasOpenCommand = commandRepository.findByServiceId(serviceId).stream()
                .anyMatch(c -> !COMMAND_TERMINAL_STATUSES.contains(c.getStatus()));

        if (hasOpenCommand) {
            throw new BusinessException(
                    "Nao e possivel encerrar o atendimento: existem comandas ainda nao fechadas/canceladas");
        }
    }

    private void validateTransition(String from, String to) {
        Set<String> allowed = ALLOWED_TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new BusinessException("Transicao de status invalida para atendimento: " + from + " -> " + to);
        }
    }

    com.restaurante.sistema.modules.dinein.domain.Service getOrThrow(Long id) {
        return serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service", id));
    }

    private ServiceResponse toResponse(com.restaurante.sistema.modules.dinein.domain.Service s) {
        return new ServiceResponse(
                s.getId(), s.getTable().getId(), s.getTable().getNumber(),
                s.getPartySize(), s.getStatus(), s.getNotes(), s.getOpenedAt(), s.getClosedAt()
        );
    }
}
