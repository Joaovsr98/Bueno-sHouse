package com.restaurante.sistema.modules.customers.service;

import com.restaurante.sistema.modules.customers.domain.Customer;
import com.restaurante.sistema.modules.customers.repository.CustomerRepository;
import com.restaurante.sistema.modules.identity.port.CustomerAccounts;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Implementa, no modulo de clientes, o que o cadastro publico (identity) precisa. */
@Component
public class CustomerAccountsAdapter implements CustomerAccounts {

    private final CustomerRepository customerRepository;

    public CustomerAccountsAdapter(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    public boolean phoneInUse(String phone) {
        return customerRepository.findByPhone(phone).isPresent();
    }

    @Override
    public void createForUser(Long userId, String fullName, String email, String phone) {
        Customer customer = new Customer();
        customer.setUserId(userId);
        customer.setFullName(fullName);
        customer.setPhone(phone);
        customer.setEmail(email);
        customer.setCreatedAt(Instant.now());
        customerRepository.save(customer);
    }
}
