package com.maramigo.catalog;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.List;

/** Public, read-only catalog. @author Maram Abbas */
@RestController
@RequestMapping("/api/products")
public class CatalogController {
    private final ProductRepository products;
    public CatalogController(ProductRepository products) { this.products = products; }
    public record ProductView(Long id, String sku, String name, String description,
                              String category, BigDecimal price, int stockQuantity, Long companyId) {
        static ProductView of(Product p) {
            return new ProductView(p.getId(), p.getSku(), p.getName(), p.getDescription(),
                    p.getCategory(), p.getPrice(), p.getStockQuantity(), p.getCompanyId());
        }
    }
    public record CatalogPage(List<ProductView> items, long total, int page, int size) {}
    @GetMapping
    public CatalogPage list(@RequestParam(defaultValue = "") @Size(max = 100) String search,
            @RequestParam(required=false) @Positive Long companyId,
            @RequestParam(defaultValue = "") @Pattern(regexp="|Living|Wear|Accessories|Wellness") String category,
            @RequestParam(defaultValue = "featured") @Pattern(regexp="featured|price-asc|price-desc|name") String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(48) int size) {
        Sort ordering = switch (sort) {
            case "price-asc" -> Sort.by("price").and(Sort.by("id"));
            case "price-desc" -> Sort.by(Sort.Direction.DESC,"price").and(Sort.by("id"));
            case "name" -> Sort.by("name").and(Sort.by("id"));
            default -> Sort.by("id");
        };
        var pagination = PageRequest.of(page, size, ordering);
        var result = companyId != null ? products.findCompanyCatalog(companyId,search.trim(),category,pagination) : category.isEmpty()
            ? products.findByActiveTrueAndNameContainingIgnoreCase(search.trim(), pagination)
            : products.findByActiveTrueAndNameContainingIgnoreCaseAndCategory(search.trim(), category, pagination);
        return new CatalogPage(result.map(ProductView::of).getContent(), result.getTotalElements(), page, size);
    }
    @GetMapping("/{id}")
    public ProductView detail(@PathVariable Long id) {
        return products.findByIdAndActiveTrue(id).map(ProductView::of)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }
    @GetMapping("/lookup")
    public List<ProductView> lookup(@RequestParam @Size(min=1, max=50) List<@NotNull @Positive Long> ids) {
        return products.findByActiveTrueAndIdIn(ids.stream().distinct().toList())
            .stream().map(ProductView::of).toList();
    }
}
