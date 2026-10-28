package com.restaurante.sistema.modules.identity.port;

/**
 * O que o cadastro publico (identity) precisa do modulo de clientes, sem conhece-lo:
 * o modulo customers implementa esta interface. Assim a dependencia fica em um
 * sentido so (customers -> identity) e nao ha ciclo entre os dois modulos.
 */
public interface CustomerAccounts {

    /** true se ja existe cliente com este telefone (RN10: telefone unico -> 422). */
    boolean phoneInUse(String phone);

    /** Cria o cadastro de cliente vinculado ao usuario recem-criado. */
    void createForUser(Long userId, String fullName, String email, String phone);
}
