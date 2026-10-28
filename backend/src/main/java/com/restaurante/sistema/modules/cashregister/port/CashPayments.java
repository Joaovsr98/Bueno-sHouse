package com.restaurante.sistema.modules.cashregister.port;

import java.math.BigDecimal;

/**
 * O que o caixa precisa dos pagamentos para conferir o fechamento: o total recebido
 * em dinheiro naquele caixa. Implementada pelo modulo de pagamentos, que ja depende
 * do caixa; assim nao ha ciclo payments <-> cashregister.
 */
public interface CashPayments {

    BigDecimal approvedCashTotal(Long cashRegisterId);
}
