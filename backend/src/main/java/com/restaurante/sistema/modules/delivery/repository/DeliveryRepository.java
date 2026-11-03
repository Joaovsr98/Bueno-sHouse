package com.restaurante.sistema.modules.delivery.repository;

import com.restaurante.sistema.modules.delivery.domain.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
    Optional<Delivery> findByOrderId(Long orderId);
    List<Delivery> findByStatus(String status);

    /** Fila do motoboy: entregas liberadas (pedido pronto), da que espera ha mais tempo para a mais nova. */
    List<Delivery> findByStatusAndReleasedAtIsNotNullOrderByReleasedAtAscIdAsc(String status);
    List<Delivery> findByCourierIdAndStatusIn(Long courierId, List<String> statuses);

    /** Entregas em andamento ainda sem localizacao (ver DeliveryLocationBackfill). */
    List<Delivery> findTop20ByLatitudeIsNullAndStatusInOrderByIdAsc(List<String> statuses);

    @Transactional
    @Modifying
    @Query("update Delivery d set d.latitude = :lat, d.longitude = :lng where d.id = :id")
    int saveLocation(@Param("id") Long id, @Param("lat") BigDecimal lat, @Param("lng") BigDecimal lng);
}
