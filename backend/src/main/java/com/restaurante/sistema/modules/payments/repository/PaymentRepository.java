package com.restaurante.sistema.modules.payments.repository;

import com.restaurante.sistema.modules.payments.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByOrderId(Long orderId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.orderId = :orderId AND p.status = 'APROVADO'")
    BigDecimal sumApprovedByOrderId(@Param("orderId") Long orderId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.cashRegisterId = :cashRegisterId " +
           "AND p.method = 'DINHEIRO' AND p.status = 'APROVADO'")
    BigDecimal sumApprovedCashByCashRegisterId(@Param("cashRegisterId") Long cashRegisterId);
}
