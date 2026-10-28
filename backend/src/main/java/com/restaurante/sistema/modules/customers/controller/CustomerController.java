package com.restaurante.sistema.modules.customers.controller;

import com.restaurante.sistema.common.exception.ResourceNotFoundException;
import com.restaurante.sistema.modules.customers.dto.*;
import com.restaurante.sistema.modules.customers.service.CustomerService;
import com.restaurante.sistema.modules.identity.security.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Equipe (garcom, caixa, gerencia) cadastra e consulta clientes no dia a dia.
 * Um usuario CLIENTE so enxerga e altera o proprio cadastro: qualquer outro id
 * devolve 404 (nao 403), para nao revelar que o cliente existe.
 */
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private static final String STAFF = "hasAnyRole('ADMINISTRADOR','GERENTE','GARCOM','CAIXA')";
    private static final String STAFF_OR_CLIENTE = "hasAnyRole('ADMINISTRADOR','GERENTE','GARCOM','CAIXA','CLIENTE')";

    private final CustomerService customerService;
    private final CurrentUserProvider currentUserProvider;

    public CustomerController(CustomerService customerService, CurrentUserProvider currentUserProvider) {
        this.customerService = customerService;
        this.currentUserProvider = currentUserProvider;
    }

    /** Cadastro do proprio cliente logado (app do cliente). */
    @GetMapping("/me")
    public CustomerResponse me() {
        return customerService.findByUserId(currentUserProvider.getCurrentUserId());
    }

    @GetMapping("/{id}")
    @PreAuthorize(STAFF_OR_CLIENTE)
    public CustomerResponse findById(@PathVariable Long id) {
        requireOwnerIfCliente(id);
        return customerService.findById(id);
    }

    @GetMapping("/by-phone/{phone}")
    @PreAuthorize(STAFF)
    public CustomerResponse findByPhone(@PathVariable String phone) {
        return customerService.findByPhone(phone);
    }

    @PostMapping
    @PreAuthorize(STAFF)
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@Valid @RequestBody CustomerRequest request) {
        return customerService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize(STAFF_OR_CLIENTE)
    public CustomerResponse update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        requireOwnerIfCliente(id);
        return customerService.update(id, request);
    }

    @GetMapping("/{id}/addresses")
    @PreAuthorize(STAFF_OR_CLIENTE)
    public List<CustomerAddressResponse> listAddresses(@PathVariable Long id) {
        requireOwnerIfCliente(id);
        return customerService.listAddresses(id);
    }

    @PostMapping("/{id}/addresses")
    @PreAuthorize(STAFF_OR_CLIENTE)
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerAddressResponse addAddress(@PathVariable Long id, @Valid @RequestBody CustomerAddressRequest request) {
        requireOwnerIfCliente(id);
        return customerService.addAddress(id, request);
    }

    private void requireOwnerIfCliente(Long customerId) {
        if (!"CLIENTE".equals(currentUserProvider.getCurrentUser().getProfile().getName())) {
            return;
        }
        Long myId = customerService.customerIdOfUser(currentUserProvider.getCurrentUserId());
        if (!myId.equals(customerId)) {
            throw new ResourceNotFoundException("Customer", customerId);
        }
    }
}
