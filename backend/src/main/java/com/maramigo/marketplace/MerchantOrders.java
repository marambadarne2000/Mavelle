package com.maramigo.marketplace;
import com.maramigo.accounts.AccountService;
import com.maramigo.orders.OrderController;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.*;
/** Snapshot ownership and locked per-store fulfilment. @author Maram Abbas */
@RestController @RequestMapping("/api/merchant/orders") @Transactional(readOnly=true)
public class MerchantOrders {
 private final JdbcTemplate db;private final ObjectMapper json;
 public MerchantOrders(JdbcTemplate db,ObjectMapper json){this.db=db;this.json=json;}
 private long company(long user){var rows=db.queryForList("SELECT company_id FROM merchant_stores WHERE user_id=?",user);if(rows.isEmpty())throw MarketplaceController.fail(HttpStatus.NOT_FOUND,"Store not found.");return ((Number)rows.get(0).get("company_id")).longValue();}
 @GetMapping public List<Map<String,Object>> list(@AuthenticationPrincipal AccountService.Principal user,@RequestParam(defaultValue="0") @Min(0) int page){return db.queryForList("SELECT o.order_number,m.status,m.version,o.created_at, sum(i.unit_price*i.quantity) AS store_total FROM merchant_orders m JOIN orders o ON o.id=m.order_id JOIN order_items i ON i.order_id=m.order_id AND i.company_id=m.company_id WHERE m.company_id=? GROUP BY o.id,m.status,m.version ORDER BY o.id DESC LIMIT 20 OFFSET ?",company(user.id()),(long)page*20);}
 @GetMapping("/{number}") public Map<String,Object> detail(@PathVariable String number,@AuthenticationPrincipal AccountService.Principal user){return view(number,company(user.id()));}
 private Map<String,Object> view(String number,long company){
  var rows=db.queryForList("SELECT o.id,o.order_number,m.status,m.version,o.shipping::text AS shipping FROM merchant_orders m JOIN orders o ON o.id=m.order_id WHERE o.order_number=? AND m.company_id=?",number,company);
  if(rows.isEmpty())throw MarketplaceController.fail(HttpStatus.NOT_FOUND,"Order not found.");var result=new LinkedHashMap<>(rows.get(0));long id=((Number)result.remove("id")).longValue();
  try{result.put("shipping",json.readValue((String)result.get("shipping"),OrderController.Shipping.class));}catch(Exception e){throw new IllegalStateException("Could not read shipping.",e);}
  result.put("items",db.queryForList("SELECT product_id,sku,name,unit_price,quantity FROM order_items WHERE order_id=? AND company_id=? ORDER BY id",id,company));
  return result;
 }
 public record Change(@NotNull @Pattern(regexp="SHIPPED|DELIVERED") String status,@Min(0) long version,@NotBlank @Size(max=240) String reason){}
 @PatchMapping("/{number}/status") @Transactional public Map<String,Object> change(@PathVariable String number,@AuthenticationPrincipal AccountService.Principal user,@Valid @RequestBody Change form){
  long company=company(user.id());
  // Parent first: admin cancellation and all store transitions serialize together.
  var parents=db.queryForList("SELECT o.id,o.status FROM orders o JOIN merchant_orders m ON m.order_id=o.id WHERE o.order_number=? AND m.company_id=? FOR UPDATE OF o",number,company);
  if(parents.isEmpty())throw MarketplaceController.fail(HttpStatus.NOT_FOUND,"Order not found.");long id=((Number)parents.get(0).get("id")).longValue();
  var row=db.queryForList("SELECT status,version FROM merchant_orders WHERE order_id=? AND company_id=? FOR UPDATE",id,company).get(0);String before=(String)row.get("status");
  if(((Number)row.get("version")).longValue()!=form.version())throw MarketplaceController.fail(HttpStatus.CONFLICT,"This order changed. Reload before updating it.");
  if(!(before.equals("PENDING")&&form.status().equals("SHIPPED")||before.equals("SHIPPED")&&form.status().equals("DELIVERED")))throw MarketplaceController.fail(HttpStatus.CONFLICT,"This order status transition is not allowed.");
  db.update("UPDATE merchant_orders SET status=?,version=version+1 WHERE order_id=? AND company_id=?",form.status(),id,company);
  db.update("INSERT INTO merchant_order_history(order_id,company_id,actor_id,from_status,to_status,reason) VALUES (?,?,?,?,?,?)",id,company,user.id(),before,form.status(),form.reason().trim());
  var states=db.queryForList("SELECT status FROM merchant_orders WHERE order_id=? AND status<>'CANCELLED'",String.class,id);
  String aggregate=states.stream().allMatch("DELIVERED"::equals)?"DELIVERED":states.stream().anyMatch(s->s.equals("SHIPPED")||s.equals("DELIVERED"))?"SHIPPED":"PENDING";
  db.update("UPDATE orders SET status=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",aggregate,id);
  if(!aggregate.equals(parents.get(0).get("status")))db.update("INSERT INTO order_status_history(order_id,actor_id,from_status,to_status,reason) VALUES (?,?,?,?,?)",id,user.id(),parents.get(0).get("status"),aggregate,form.reason().trim());
  return view(number,company);
 }
}
