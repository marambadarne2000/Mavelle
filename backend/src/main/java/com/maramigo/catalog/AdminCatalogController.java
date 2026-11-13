package com.maramigo.catalog;

import com.maramigo.accounts.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Locale;

/** @author Maram Abbas */
@RestController
@RequestMapping("/api/admin/products")
public class AdminCatalogController {
    private final ProductManagement service;
    public AdminCatalogController(ProductManagement service) { this.service = service; }

    public record ProductData(
            @NotBlank @Pattern(regexp="[A-Z0-9][A-Z0-9_-]{1,79}") String sku,
            @NotBlank @Size(max=160) String name,
            @NotBlank @Size(max=4000) String description,
            @NotNull @Pattern(regexp="Living|Wear|Accessories|Wellness|Makeup|Games|Electronics|Other") String category,
            @NotNull @DecimalMin(value="0.00", inclusive=false) @Digits(integer=10, fraction=2) BigDecimal price,
            @NotNull Boolean active, @Positive Long companyId) {
        public ProductData {
            sku = sku == null ? null : sku.trim().toUpperCase(Locale.ROOT);
            name = name == null ? null : name.trim();
            description = description == null ? null : description.trim();
        }
    }
    public record Create(@NotNull @Valid ProductData product, @NotNull @Min(0) @Max(1000000) Integer initialStock) {}
    public record Edit(@NotNull @Valid ProductData product, @NotNull @PositiveOrZero Long version) {}
    public record StockChange(@NotNull @Min(-1000000) @Max(1000000) Integer delta,
            @NotBlank @Size(max=240) String reason, @NotNull @PositiveOrZero Long version) {}

    @GetMapping public ProductManagement.ProductPage list(@RequestParam(defaultValue="") @Size(max=100) String search,
            @RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="12") @Min(1) @Max(48) int size) {
        return service.list(search.trim(), page, size);
    }
    @GetMapping("/{id}") public ProductManagement.ProductView detail(@PathVariable Long id) { return service.detail(id); }
    @PostMapping public ResponseEntity<ProductManagement.ProductView> create(@Valid @RequestBody Create form,
            @AuthenticationPrincipal AccountService.Principal actor) {
        var result = service.create(form, actor.id());
        return ResponseEntity.created(URI.create("/api/admin/products/" + result.id())).body(result);
    }
    @PutMapping("/{id}") public ProductManagement.ProductView edit(@PathVariable Long id, @Valid @RequestBody Edit form) {
        return service.edit(id, form);
    }
    @PatchMapping("/{id}/stock") public ProductManagement.ProductView stock(@PathVariable Long id,
            @Valid @RequestBody StockChange form, @AuthenticationPrincipal AccountService.Principal actor) {
        return service.adjust(id, form, actor.id());
    }
    @GetMapping("/{id}/inventory-movements") public ProductManagement.MovementPage history(@PathVariable Long id,
            @RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="12") @Min(1) @Max(48) int size) {
        return service.history(id, page, size);
    }
}
