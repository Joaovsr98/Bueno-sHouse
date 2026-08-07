package com.restaurante.sistema.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Cache em memoria (ConcurrentMapCacheManager, autoconfigurado pelo Spring
 * Boot na ausencia de uma lib de cache dedicada) para consultas de leitura
 * pesada e baixa frequencia de escrita: o cardapio (categorias/produtos).
 *
 * Nao usar para dados que mudam com frequencia ou que precisam de leitura
 * sempre consistente (ex.: status de pedido, saldo de caixa).
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
