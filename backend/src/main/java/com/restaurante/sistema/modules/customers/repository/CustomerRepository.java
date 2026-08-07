package com.restaurante.sistema.modules.customers.repository;

import com.restaurante.sistema.modules.customers.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByPhone(String phone);
    Optional<Customer> findByUserId(Long userId);
}
