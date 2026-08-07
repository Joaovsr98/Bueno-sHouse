package com.restaurante.sistema.modules.dinein.service;

import com.restaurante.sistema.common.exception.BusinessException;
import com.restaurante.sistema.common.exception.ResourceNotFoundException;
import com.restaurante.sistema.modules.dinein.domain.Command;
import com.restaurante.sistema.modules.dinein.dto.CommandResponse;
import com.restaurante.sistema.modules.dinein.repository.CommandRepository;
import com.restaurante.sistema.modules.identity.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Etapa 7. Maquina de estado de Command (Etapa 2):
 *   ABERTA -> EM_ATENDIMENTO -> AGUARDANDO_PAGAMENTO -> (PARCIALMENTE_PAGA ->)* FECHADA
 *   qualquer estado nao-FECHADA -> CANCELADA (com justificativa)
 *
 * O fechamento definitivo com base em saldo zerado (invariante "uma comanda
 * nao fecha com saldo devedor") depende do modulo payments, que ainda nao
 * existe - por ora, o fechamento e uma transicao explicita permitida sem essa
 * validacao, e sera reforcada quando o modulo payments for implementado.
 */
@Service
public class CommandService {

    private static final java.util.Map<String, Set<String>> ALLOWED_TRANSITIONS = java.util.Map.of(
            "ABERTA", Set.of("EM_ATENDIMENTO", "AGUARDANDO_PAGAMENTO", "CANCELADA"),
            "EM_ATENDIMENTO", Set.of("AGUARDANDO_PAGAMENTO", "CANCELADA"),
            "AGUARDANDO_PAGAMENTO", Set.of("PARCIALMENTE_PAGA", "FECHADA", "CANCELADA"),
            "PARCIALMENTE_PAGA", Set.of("FECHADA", "CANCELADA"),
            "FECHADA", Set.of(),
            "CANCELADA", Set.of()
    );

    private final CommandRepository commandRepository;
    private final ServiceService serviceService;
    private final CurrentUserProvider currentUserProvider;

    public CommandService(
            CommandRepository commandRepository,
            ServiceService serviceService,
            CurrentUserProvider currentUserProvider
    ) {
        this.commandRepository = commandRepository;
        this.serviceService = serviceService;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public List<CommandResponse> listByService(Long serviceId) {
        return commandRepository.findByServiceId(serviceId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public CommandResponse open(Long serviceId) {
        com.restaurante.sistema.modules.dinein.domain.Service service = serviceService.getOrThrow(serviceId);

        if (!Set.of("OPEN", "IN_SERVICE").contains(service.getStatus())) {
            throw new BusinessException(
                    "Nao e possivel abrir comanda: atendimento nao esta ativo (status: " + service.getStatus() + ")");
        }

        Command command = new Command();
        command.setService(service);
        command.setOpenedBy(currentUserProvider.getCurrentUserId());
        command.setStatus("ABERTA");
        command.setOpenedAt(Instant.now());

        Command saved = commandRepository.save(command);

        // Primeira comanda de um atendimento OPEN o avanca para IN_SERVICE
        if ("OPEN".equals(service.getStatus())) {
            serviceService.transitionTo(service.getId(), "IN_SERVICE");
        }

        return toResponse(saved);
    }

    @Transactional
    public CommandResponse transitionTo(Long commandId, String newStatus) {
        Command command = getOrThrow(commandId);
        validateTransition(command.getStatus(), newStatus);

        if (Set.of("FECHADA", "CANCELADA").contains(newStatus)) {
            command.setClosedAt(Instant.now());
        }

        command.setStatus(newStatus);
        return toResponse(commandRepository.save(command));
    }

    private void validateTransition(String from, String to) {
        Set<String> allowed = ALLOWED_TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new BusinessException("Transicao de status invalida para comanda: " + from + " -> " + to);
        }
    }

    private Command getOrThrow(Long id) {
        return commandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Command", id));
    }

    private CommandResponse toResponse(Command c) {
        return new CommandResponse(c.getId(), c.getService().getId(), c.getStatus(), c.getOpenedAt(), c.getClosedAt());
    }
}
