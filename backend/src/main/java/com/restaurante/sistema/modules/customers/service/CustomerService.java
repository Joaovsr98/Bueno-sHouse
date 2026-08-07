package com.restaurante.sistema.modules.customers.service;

import com.restaurante.sistema.common.exception.BusinessException;
import com.restaurante.sistema.common.exception.ResourceNotFoundException;
import com.restaurante.sistema.modules.customers.domain.Customer;
import com.restaurante.sistema.modules.customers.domain.CustomerAddress;
import com.restaurante.sistema.modules.customers.dto.*;
import com.restaurante.sistema.modules.customers.repository.CustomerAddressRepository;
import com.restaurante.sistema.modules.customers.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository addressRepository;

    public CustomerService(CustomerRepository customerRepository, CustomerAddressRepository addressRepository) {
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
    }

    @Transactional(readOnly = true)
    public CustomerResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public CustomerResponse findByPhone(String phone) {
        Customer customer = customerRepository.findByPhone(phone)
                .orElseThrow(() -> new ResourceNotFoundException("Customer com telefone", phone));
        return toResponse(customer);
    }

    /** Cadastro do cliente logado (perfil CLIENTE). */
    @Transactional(readOnly = true)
    public CustomerResponse findByUserId(Long userId) {
        Customer customer = customerRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente vinculado ao usuario", userId));
        return toResponse(customer);
    }

    /** Id do cliente vinculado ao usuario logado (para regras de propriedade). */
    @Transactional(readOnly = true)
    public Long customerIdOfUser(Long userId) {
        return customerRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("Usuario cliente sem cadastro de cliente vinculado"))
                .getId();
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        if (customerRepository.findByPhone(request.phone()).isPresent()) {
            throw new BusinessException("Ja existe um cliente cadastrado com este telefone");
        }

        Customer customer = new Customer();
        applyRequest(customer, request);
        customer.setCreatedAt(Instant.now());

        return toResponse(customerRepository.save(customer));
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer customer = getOrThrow(id);
        applyRequest(customer, request);
        return toResponse(customerRepository.save(customer));
    }

    @Transactional
    public CustomerAddressResponse addAddress(Long customerId, CustomerAddressRequest request) {
        Customer customer = getOrThrow(customerId);

        CustomerAddress address = new CustomerAddress();
        address.setCustomer(customer);
        address.setLabel(request.label());
        address.setStreet(request.street());
        address.setNumber(request.number());
        address.setComplement(request.complement());
        address.setNeighborhood(request.neighborhood());
        address.setCity(request.city());
        address.setState(request.state());
        address.setZipCode(request.zipCode());
        address.setReferencePoint(request.referencePoint());
        address.setDefault(request.isDefault() != null && request.isDefault());

        CustomerAddress saved = addressRepository.save(address);
        return toAddressResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CustomerAddressResponse> listAddresses(Long customerId) {
        return addressRepository.findByCustomerId(customerId).stream().map(this::toAddressResponse).toList();
    }

    private void applyRequest(Customer customer, CustomerRequest request) {
        customer.setFullName(request.fullName());
        customer.setPhone(request.phone());
        customer.setEmail(request.email());
        customer.setDocument(request.document());
        customer.setBirthDate(request.birthDate());
    }

    private Customer getOrThrow(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }

    private CustomerResponse toResponse(Customer c) {
        List<CustomerAddressResponse> addresses = c.getAddresses().stream().map(this::toAddressResponse).toList();
        return new CustomerResponse(c.getId(), c.getFullName(), c.getPhone(), c.getEmail(), c.getDocument(), c.getBirthDate(), addresses);
    }

    private CustomerAddressResponse toAddressResponse(CustomerAddress a) {
        return new CustomerAddressResponse(
                a.getId(), a.getLabel(), a.getStreet(), a.getNumber(), a.getComplement(),
                a.getNeighborhood(), a.getCity(), a.getState(), a.getZipCode(), a.getReferencePoint(), a.isDefault()
        );
    }
}
