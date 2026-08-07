package com.restaurante.sistema;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada da aplicacao.
 *
 * Etapa 4 - Fundacao Tecnica: esta classe apenas inicializa o contexto Spring.
 * Nenhum modulo de negocio (catalogo, pedidos, cozinha, etc.) foi implementado ainda.
 * Os modulos serao adicionados nas etapas seguintes (5 em diante), cada um em seu
 * proprio pacote sob com.restaurante.sistema.modules.*
 */
@SpringBootApplication
public class RestauranteApplication {

    public static void main(String[] args) {
        SpringApplication.run(RestauranteApplication.class, args);
    }
}
