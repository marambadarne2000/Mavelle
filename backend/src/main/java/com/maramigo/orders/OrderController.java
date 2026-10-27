package com.maramigo.orders;
import com.maramigo.accounts.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.math.BigDecimal;
import java.util.List;

/** @author Maram Abbas */
@RestController @RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orders;
    public OrderController(OrderService orders){this.orders=orders;}
    public record Shipping(@NotBlank @Size(max=120) String fullName,@NotBlank @Size(max=40) String phone,
        @NotBlank @Size(max=240) String address,@NotBlank @Size(max=100) String city,
        @NotBlank @Size(max=20) String postalCode,@NotNull @Pattern(regexp="[A-Z]{2}") String country) {
        public Shipping {fullName=trim(fullName);phone=trim(phone);address=trim(address);city=trim(city);postalCode=trim(postalCode);country=trim(country);}
        private static String trim(String s){return s==null?null:s.trim();}
    }
    public record Line(@NotNull @Positive Long productId,@NotNull @Min(1) @Max(99) Integer quantity,
        @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=10,fraction=2) BigDecimal expectedPrice){}
    public record Checkout(@NotNull @Size(min=1,max=50) List<@NotNull @Valid Line> items,@NotNull @Valid Shipping shipping){}
    @PostMapping public OrderService.View checkout(@Valid @RequestBody Checkout form,
        @RequestHeader("Idempotency-Key") @Pattern(regexp="[A-Za-z0-9_-]{16,64}") String key,
        @AuthenticationPrincipal AccountService.Principal user){return orders.checkout(user.id(),key,form);}
    @GetMapping public List<OrderService.Summary> mine(@AuthenticationPrincipal AccountService.Principal user,
        @RequestParam(defaultValue="0") @Min(0) int page){return orders.list(user.id(),page);}
    @GetMapping("/{number}") public OrderService.View detail(@PathVariable String number,@AuthenticationPrincipal AccountService.Principal user){return orders.view(number,user.id());}
}
