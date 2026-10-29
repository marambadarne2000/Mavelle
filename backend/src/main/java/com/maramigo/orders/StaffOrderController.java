package com.maramigo.orders;

import com.maramigo.accounts.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;

/** Fulfilment DTOs deliberately omit prices, totals and business metrics. @author Maram Abbas */
@RestController @RequestMapping("/api/staff/orders")
public class StaffOrderController {
 private final AdminOrderService orders;
 public StaffOrderController(AdminOrderService orders){this.orders=orders;}
 public record Summary(String orderNumber,String status,Instant createdAt){}
 public record Item(String sku,String name,String companyName,int quantity){}
 public record Detail(String orderNumber,String status,Instant createdAt,long version,OrderController.Shipping shipping,List<Item> items){}
 public record Page(List<Summary> items,long total,int page,int size){}
 public record Change(@NotNull @Pattern(regexp="SHIPPED|DELIVERED") String status,@NotNull @Min(0) Long version,@NotBlank @Size(max=240) String reason){}
 @GetMapping public Page list(@RequestParam(defaultValue="") @Pattern(regexp="|PENDING|SHIPPED|DELIVERED|CANCELLED") String status,@RequestParam(defaultValue="") @Size(max=100) String search,@RequestParam(defaultValue="0") @Min(0) int page){
  var result=orders.list(status,search.trim(),page);return new Page(result.items().stream().map(o->new Summary(o.orderNumber(),o.status(),o.createdAt())).toList(),result.total(),result.page(),result.size());
 }
 @GetMapping("/{number}") public Detail detail(@PathVariable String number){return project(orders.detail(number));}
 @PatchMapping("/{number}/status") public Detail change(@PathVariable String number,@Valid @RequestBody Change form,@AuthenticationPrincipal AccountService.Principal actor){
  if(form.reason().trim().isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A reason is required.");
  return project(orders.change(number,new AdminOrderController.Change(form.status(),form.version(),form.reason().trim()),actor.id()));
 }
 private Detail project(AdminOrderService.Detail detail){var order=detail.order();return new Detail(order.orderNumber(),order.status(),order.createdAt(),detail.version(),order.shipping(),order.items().stream().map(i->new Item(i.sku(),i.name(),i.companyName(),i.quantity())).toList());}
}
