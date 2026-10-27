package com.maramigo.catalog;

import jakarta.persistence.*;
import java.time.Instant;

/** @author Maram Abbas */
@Entity
@Table(name = "inventory_movements")
public class InventoryMovement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long productId;
    private Long actorId;
    private int quantityDelta;
    private String reason;
    private Instant occurredAt;
    protected InventoryMovement() {}
    public InventoryMovement(Long productId, Long actorId, int delta, String reason) {
        this.productId = productId; this.actorId = actorId;
        this.quantityDelta = delta; this.reason = reason; this.occurredAt = Instant.now();
    }
    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public Long getActorId() { return actorId; }
    public int getQuantityDelta() { return quantityDelta; }
    public String getReason() { return reason; }
    public Instant getOccurredAt() { return occurredAt; }
}
