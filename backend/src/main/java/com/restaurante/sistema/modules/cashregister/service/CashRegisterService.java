package com.restaurante.sistema.modules.cashregister.service;

import com.restaurante.sistema.common.exception.BusinessException;
import com.restaurante.sistema.common.exception.ResourceNotFoundException;
import com.restaurante.sistema.modules.cashregister.domain.CashMovement;
import com.restaurante.sistema.modules.cashregister.domain.CashRegister;
import com.restaurante.sistema.modules.cashregister.dto.*;
import com.restaurante.sistema.modules.cashregister.repository.CashMovementRepository;
import com.restaurante.sistema.modules.cashregister.repository.CashRegisterRepository;
import com.restaurante.sistema.modules.identity.security.CurrentUserProvider;
import com.restaurante.sistema.modules.payments.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Etapa 9 - modulo cashregister.
 *
 * Regra de fechamento (Etapa 1/3): saldo esperado = saldo de abertura
 * + entradas em dinheiro (pagamentos DINHEIRO aprovados durante a sessao do
 * caixa) + movimentos ENTRADA/SUPRIMENTO - movimentos SAIDA/SANGRIA. A
 * diferenca (closingBalance - expectedBalance) fica registrada para
 * conferencia - o fechamento NUNCA falha por diferenca, apenas a documenta
 * (bater ou nao o caixa e uma informacao operacional, nao um erro de
 * sistema).
 *
 * Concorrencia: "um caixa aberto por unidade" e garantido em duas camadas -
 * indice unico no banco (open_flag, Etapa 3) e checagem explicita aqui antes
 * do INSERT, pelo mesmo motivo do ServiceService (Etapa 7): dar erro legivel
 * em vez de estourar a constraint.
 */
@Service
public class CashRegisterService {

    private static final Set<String> CASH_IN_TYPES = Set.of("ENTRADA", "SUPRIMENTO");
    private static final Set<String> CASH_OUT_TYPES = Set.of("SAIDA", "SANGRIA");

    private final CashRegisterRepository cashRegisterRepository;
    private final CashMovementRepository cashMovementRepository;
    private final PaymentRepository paymentRepository;
    private final CurrentUserProvider currentUserProvider;

    public CashRegisterService(
            CashRegisterRepository cashRegisterRepository,
            CashMovementRepository cashMovementRepository,
            PaymentRepository paymentRepository,
            CurrentUserProvider currentUserProvider
    ) {
        this.cashRegisterRepository = cashRegisterRepository;
        this.cashMovementRepository = cashMovementRepository;
        this.paymentRepository = paymentRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public CashRegisterResponse findCurrentOpen(Long unitId) {
        CashRegister register = cashRegisterRepository.findByUnitIdAndOpenFlag(unitId, 1)
                .orElseThrow(() -> new ResourceNotFoundException("Caixa aberto para a unidade", unitId));
        return toResponse(register);
    }

    @Transactional
    public CashRegisterResponse open(OpenCashRegisterRequest request) {
        boolean alreadyOpen = cashRegisterRepository.findByUnitIdAndOpenFlag(request.unitId(), 1).isPresent();
        if (alreadyOpen) {
            throw new BusinessException("Ja existe um caixa aberto para esta unidade");
        }

        CashRegister register = new CashRegister();
        register.setUnitId(request.unitId());
        register.setOpenedBy(currentUserProvider.getCurrentUserId());
        register.setOpeningBalance(request.openingBalance());
        register.setOpenedAt(Instant.now());

        return toResponse(cashRegisterRepository.save(register));
    }

    @Transactional
    public CashRegisterResponse close(Long id, CloseCashRegisterRequest request) {
        CashRegister register = getOrThrow(id);

        if (register.getClosedAt() != null) {
            throw new BusinessException("Este caixa ja foi fechado");
        }

        BigDecimal cashIn = sumMovements(id, CASH_IN_TYPES);
        BigDecimal cashOut = sumMovements(id, CASH_OUT_TYPES);
        BigDecimal cashPayments = paymentRepository.sumApprovedCashByCashRegisterId(id);

        BigDecimal expectedBalance = register.getOpeningBalance()
                .add(cashIn)
                .add(cashPayments)
                .subtract(cashOut);

        register.setClosingBalance(request.closingBalance());
        register.setExpectedBalance(expectedBalance);
        register.setDifference(request.closingBalance().subtract(expectedBalance));
        register.setClosedBy(currentUserProvider.getCurrentUserId());
        register.setClosedAt(Instant.now());

        return toResponse(cashRegisterRepository.save(register));
    }

    @Transactional
    public CashMovementResponse registerMovement(Long cashRegisterId, CashMovementRequest request) {
        CashRegister register = getOrThrow(cashRegisterId);

        if (register.getClosedAt() != null) {
            throw new BusinessException("Nao e possivel registrar movimento em um caixa ja fechado");
        }

        if (!CASH_IN_TYPES.contains(request.type()) && !CASH_OUT_TYPES.contains(request.type())) {
            throw new BusinessException("Tipo de movimento invalido: " + request.type());
        }

        CashMovement movement = new CashMovement();
        movement.setCashRegister(register);
        movement.setType(request.type());
        movement.setAmount(request.amount());
        movement.setReason(request.reason());
        movement.setRegisteredBy(currentUserProvider.getCurrentUserId());
        movement.setCreatedAt(Instant.now());

        CashMovement saved = cashMovementRepository.save(movement);
        return new CashMovementResponse(saved.getId(), saved.getType(), saved.getAmount(), saved.getReason(), saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<CashMovementResponse> listMovements(Long cashRegisterId) {
        return cashMovementRepository.findByCashRegisterId(cashRegisterId).stream()
                .map(m -> new CashMovementResponse(m.getId(), m.getType(), m.getAmount(), m.getReason(), m.getCreatedAt()))
                .toList();
    }

    private BigDecimal sumMovements(Long cashRegisterId, Set<String> types) {
        return cashMovementRepository.findByCashRegisterId(cashRegisterId).stream()
                .filter(m -> types.contains(m.getType()))
                .map(CashMovement::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    CashRegister getOrThrow(Long id) {
        return cashRegisterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CashRegister", id));
    }

    private CashRegisterResponse toResponse(CashRegister r) {
        return new CashRegisterResponse(
                r.getId(), r.getUnitId(), r.getOpeningBalance(), r.getClosingBalance(), r.getExpectedBalance(),
                r.getDifference(), r.getOpenedAt(), r.getClosedAt(), r.getClosedAt() == null
        );
    }
}
