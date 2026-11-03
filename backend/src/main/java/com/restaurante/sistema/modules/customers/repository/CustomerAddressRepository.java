package com.restaurante.sistema.modules.customers.repository;

import com.restaurante.sistema.modules.customers.domain.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {
    List<CustomerAddress> findByCustomerId(Long customerId);

    /** Enderecos que ainda nao tiveram a localizacao buscada (os mais antigos primeiro). */
    List<CustomerAddress> findTop20ByGeocodedAtIsNullOrderByIdAsc();

    /** Grava so a localizacao (sem sobrescrever o resto do endereco, editado ao mesmo tempo ou nao). */
    @Transactional
    @Modifying
    @Query("update CustomerAddress a set a.latitude = :lat, a.longitude = :lng, a.geocodedAt = :at where a.id = :id")
    int saveLocation(@Param("id") Long id, @Param("lat") BigDecimal lat, @Param("lng") BigDecimal lng,
                     @Param("at") Instant at);
}
