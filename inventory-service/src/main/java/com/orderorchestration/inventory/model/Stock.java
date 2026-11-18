package com.orderorchestration.inventory.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "stock", schema = "inventory_service")
public class Stock {

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int available;

    protected Stock() {
    }

    public UUID getProductId() {
        return productId;
    }

    public String getName() {
        return name;
    }

    public int getAvailable() {
        return available;
    }
}
