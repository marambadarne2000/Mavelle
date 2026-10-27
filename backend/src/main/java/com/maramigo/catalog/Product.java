package com.maramigo.catalog;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** @author Maram Abbas */
@Entity
@Table(name = "products")
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String sku;
    private String name;
    private String description;
    private String category;
    private BigDecimal price;
    private int stockQuantity;
    private boolean active;
    private Long companyId = 1L;
    @Version
    private Long version;
    protected Product() {}
    public Product(String sku, String name, String description, String category, BigDecimal price, int stockQuantity, boolean active) {
        updateDetails(sku, name, description, category, price, active);
        this.stockQuantity = stockQuantity;
    }
    public void updateDetails(String sku, String name, String description, String category, BigDecimal price, boolean active) {
        this.sku = sku; this.name = name; this.description = description;
        this.category = category; this.price = price; this.active = active;
    }
    public void adjustStock(int delta) { this.stockQuantity = Math.addExact(this.stockQuantity, delta); }
    public Long getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public BigDecimal getPrice() { return price; }
    public int getStockQuantity() { return stockQuantity; }
    public boolean isActive() { return active; }
    public Long getVersion() { return version; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId=companyId; }
}
