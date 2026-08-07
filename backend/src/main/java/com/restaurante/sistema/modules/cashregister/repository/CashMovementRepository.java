package com.restaurante.sistema.modules.cashregister.repository;

import com.restaurante.sistema.modules.cashregister.domain.CashMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CashMovementRepository extends JpaRepository<CashMovement, Long> {
    List<CashMovement> findByCashRegisterId(Long cashRegisterId);
}
