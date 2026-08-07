package com.restaurante.sistema.modules.dinein.repository;

import com.restaurante.sistema.modules.dinein.domain.Service;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceRepository extends JpaRepository<Service, Long> {
    List<Service> findByTableIdOrderByOpenedAtDesc(Long tableId);

    // active_flag = 1 e o unico valor possivel para atendimento ativo (ver migration V6)
    Optional<Service> findByTableIdAndActiveFlag(Long tableId, Integer activeFlag);
}
