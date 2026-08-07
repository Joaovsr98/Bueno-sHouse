package com.restaurante.sistema.modules.customers.repository;

import com.restaurante.sistema.modules.customers.domain.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {
    List<CustomerAddress> findByCustomerId(Long customerId);
}
