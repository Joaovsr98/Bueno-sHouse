package com.restaurante.sistema.modules.dinein.repository;

import com.restaurante.sistema.modules.dinein.domain.Command;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommandRepository extends JpaRepository<Command, Long> {
    List<Command> findByServiceId(Long serviceId);
}
