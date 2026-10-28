package com.restaurante.sistema.modules.inventory.repository;

import com.restaurante.sistema.modules.inventory.domain.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {
    List<InventoryItem> findByUnitIdAndActiveTrueOrderByNameAsc(Long unitId);

    /**
     * Soma ao saldo de forma atomica no banco (sem ler-subtrair-gravar), para
     * duas transacoes simultaneas nunca perderem uma atualizacao.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE InventoryItem i SET i.currentQuantity = i.currentQuantity + :delta WHERE i.id = :id")
    int addQuantity(@Param("id") Long id, @Param("delta") BigDecimal delta);

    /**
     * Baixa atomica que so acontece se houver saldo: devolve 0 linhas quando o
     * estoque ficaria negativo (RN03 garantido no proprio UPDATE).
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE InventoryItem i SET i.currentQuantity = i.currentQuantity - :quantity "
            + "WHERE i.id = :id AND i.currentQuantity >= :quantity")
    int subtractIfAvailable(@Param("id") Long id, @Param("quantity") BigDecimal quantity);

    @Query("SELECT i FROM InventoryItem i WHERE i.unitId = :unitId AND i.active = true AND i.currentQuantity < i.minimumQuantity")
    List<InventoryItem> findBelowMinimum(@Param("unitId") Long unitId);
}
