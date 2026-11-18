package com.orderorchestration.inventory.repository;

import com.orderorchestration.inventory.model.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface StockRepository extends JpaRepository<Stock, UUID> {

    @Modifying(clearAutomatically = true)
    @Query("update Stock s set s.available = s.available - :quantity where s.productId = :productId and s.available >= :quantity")
    int decrementIfAvailable(UUID productId, int quantity);

    @Modifying(clearAutomatically = true)
    @Query("update Stock s set s.available = s.available + :quantity where s.productId = :productId")
    int increment(UUID productId, int quantity);
}
