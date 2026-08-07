package com.restaurante.sistema.modules.cashregister.repository;

import com.restaurante.sistema.modules.cashregister.domain.CashRegister;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CashRegisterRepository extends JpaRepository<CashRegister, Long> {
    Optional<CashRegister> findByUnitIdAndOpenFlag(Long unitId, Integer openFlag);
}
