package com.maramigo.catalog;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Transaction boundary for product edits and audited inventory changes. @author Maram Abbas */
@Service
@Transactional(readOnly=true)
public class ProductManagement {
    private final ProductRepository products;
    private final InventoryRepository movements;
    private final CompanyRepository companies;
    public ProductManagement(ProductRepository products, InventoryRepository movements, CompanyRepository companies) { this.products=products; this.movements=movements; this.companies=companies; }
    public record ProductView(Long id, String sku, String name, String description, String category,
            BigDecimal price, int stockQuantity, boolean active, Long version, Long companyId) {
        static ProductView of(Product p) { return new ProductView(p.getId(),p.getSku(),p.getName(),p.getDescription(),p.getCategory(),p.getPrice(),p.getStockQuantity(),p.isActive(),p.getVersion(),p.getCompanyId()); }
    }
    public record ProductPage(List<ProductView> items, long total, int page, int size) {}
    public record MovementView(Long id, Long productId, Long actorId, int delta, String reason, Instant occurredAt) {
        static MovementView of(InventoryMovement m) { return new MovementView(m.getId(),m.getProductId(),m.getActorId(),m.getQuantityDelta(),m.getReason(),m.getOccurredAt()); }
    }
    public record MovementPage(List<MovementView> items, long total, int page, int size) {}
    public ProductPage list(String search, int page, int size) {
        var found=products.findByNameContainingIgnoreCaseOrSkuContainingIgnoreCase(search,search,PageRequest.of(page,size,Sort.by("id")));
        return new ProductPage(found.map(ProductView::of).getContent(),found.getTotalElements(),page,size);
    }
    public ProductView detail(Long id) { return ProductView.of(find(id)); }
    private Product find(Long id) { return products.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found.")); }
    private Product locked(Long id, Long version) {
        Product p=products.findLockedById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found."));
        if (!Objects.equals(p.getVersion(),version)) throw new ResponseStatusException(HttpStatus.CONFLICT,"This product changed. Reload it before saving again.");
        return p;
    }
    @Transactional public ProductView create(AdminCatalogController.Create form, Long actorId) {
        var d=form.product();
        Product p=new Product(d.sku(),d.name(),d.description(),d.category(),d.price(),form.initialStock(),d.active());
        p.setCompanyId(company(d.companyId()==null?1L:d.companyId()));
        products.saveAndFlush(p);
        if (form.initialStock()>0) movements.saveAndFlush(new InventoryMovement(p.getId(),actorId,form.initialStock(),"Initial stock"));
        return ProductView.of(p);
    }
    @Transactional public ProductView edit(Long id, AdminCatalogController.Edit form) {
        var p=locked(id,form.version()); var d=form.product();
        p.updateDetails(d.sku(),d.name(),d.description(),d.category(),d.price(),d.active());
        if(d.companyId()!=null) p.setCompanyId(company(d.companyId()));
        products.flush(); return ProductView.of(p);
    }
    @Transactional public ProductView adjust(Long id, AdminCatalogController.StockChange form, Long actorId) {
        if (form.delta()==0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Quantity change must not be zero.");
        var p=locked(id,form.version());
        long next=(long)p.getStockQuantity()+form.delta();
        if (next<0 || next>1000000) throw new ResponseStatusException(HttpStatus.CONFLICT,"Stock must remain between 0 and 1,000,000.");
        p.adjustStock(form.delta()); products.flush();
        movements.saveAndFlush(new InventoryMovement(id,actorId,form.delta(),form.reason().trim()));
        return ProductView.of(p);
    }
    public MovementPage history(Long id, int page, int size) {
        find(id);
        var found=movements.findByProductId(id,PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"id")));
        return new MovementPage(found.map(MovementView::of).getContent(),found.getTotalElements(),page,size);
    }
    private Long company(Long id){if(!companies.existsById(id))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Store not found.");return id;}
}
