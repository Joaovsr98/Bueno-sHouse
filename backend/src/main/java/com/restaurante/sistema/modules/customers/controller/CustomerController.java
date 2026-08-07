package com.restaurante.sistema.modules.customers.controller;

import com.restaurante.sistema.modules.customers.dto.*;
import com.restaurante.sistema.modules.customers.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Leitura/escrita liberada para qualquer usuario autenticado (garcom e
 * atendente de delivery precisam cadastrar/consultar cliente no dia a dia).
 * Nao ha exposicao de dados entre clientes diferentes - cada consulta e por
 * ID ou telefone especifico, nunca uma listagem geral de todos os clientes.
 */
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping("/{id}")
    public CustomerResponse findById(@PathVariable Long id) {
        return customerService.findById(id);
    }

    @GetMapping("/by-phone/{phone}")
    public CustomerResponse findByPhone(@PathVariable String phone) {
        return customerService.findByPhone(phone);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@Valid @RequestBody CustomerRequest request) {
        return customerService.create(request);
    }

    @PutMapping("/{id}")
    public CustomerResponse update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return customerService.update(id, request);
    }

    @GetMapping("/{id}/addresses")
    public List<CustomerAddressResponse> listAddresses(@PathVariable Long id) {
        return customerService.listAddresses(id);
    }

    @PostMapping("/{id}/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerAddressResponse addAddress(@PathVariable Long id, @Valid @RequestBody CustomerAddressRequest request) {
        return customerService.addAddress(id, request);
    }
}
