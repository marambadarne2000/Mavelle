package com.maramigo.orders;

import com.maramigo.accounts.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Administrator-only order operations. @author Maram Abbas */
@RestController @RequestMapping("/api/admin/orders")
public class AdminOrderController {
    private final AdminOrderService orders;
    public AdminOrderController(AdminOrderService orders){this.orders=orders;}
    public record Change(@NotNull @Pattern(regexp="SHIPPED|DELIVERED|CANCELLED") String status,
        @NotNull @Min(0) Long version,@NotBlank @Size(max=240) String reason){
        public Change {reason=reason==null?null:reason.trim();}
    }
    @GetMapping public AdminOrderService.Page list(
        @RequestParam(defaultValue="") @Pattern(regexp="|PENDING|SHIPPED|DELIVERED|CANCELLED") String status,
        @RequestParam(defaultValue="") @Size(max=100) String search,
        @RequestParam(defaultValue="0") @Min(0) int page){return orders.list(status,search.trim(),page);}
    @GetMapping("/{number}") public AdminOrderService.Detail detail(@PathVariable String number){return orders.detail(number);}
    @PatchMapping("/{number}/status") public AdminOrderService.Detail change(@PathVariable String number,
        @Valid @RequestBody Change change,@AuthenticationPrincipal AccountService.Principal actor){return orders.change(number,change,actor.id());}
}
