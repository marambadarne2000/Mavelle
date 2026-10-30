package com.maramigo.marketplace;

import com.maramigo.accounts.*;
import com.maramigo.catalog.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;

/** Server-scoped merchant access; client identifiers never assign ownership. @author Maram Abbas */
@RestController @Transactional(readOnly=true)
public class MarketplaceController {
 private final JdbcTemplate db; private final PasswordEncoder passwords; private final ProductManagement products;
 public MarketplaceController(JdbcTemplate db,PasswordEncoder passwords,ProductManagement products){this.db=db;this.passwords=passwords;this.products=products;}
 public record Join(@NotBlank @Size(max=120) String ownerName,@NotBlank @Size(max=120) String businessName,
  @NotNull @Pattern(regexp="Makeup|Clothing|Games|Home|Mixed|Other") String businessType,
  @NotNull @Size(max=40) String registrationNumber,@NotBlank @Size(max=40) String phone,
  @NotBlank @Email @Size(max=254) String email,@NotBlank @Size(max=600) String description,
  @NotBlank String password,@NotBlank String confirmPassword,@AssertTrue boolean termsAccepted){}
 public record Store(long companyId,String slug,String businessName,String description,String status,String ownerName,String businessType,String registrationNumber,String phone,String email){}
 private static final String STORE="SELECT c.id,c.slug,c.name,c.description,c.status,m.owner_name,m.business_type,m.registration_number,m.phone,m.contact_email FROM merchant_stores m JOIN companies c ON c.id=m.company_id";
 private List<Store> stores(String where,Object... args){return db.query(STORE+where,(r,n)->new Store(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8),r.getString(9),r.getString(10)),args);}
 private Store own(long user){return stores(" WHERE m.user_id=?",user).stream().findFirst().orElseThrow(()->fail(HttpStatus.NOT_FOUND,"Store not found."));}
 private Store active(long user){var store=own(user);if(!store.status().equals("APPROVED"))throw fail(HttpStatus.FORBIDDEN,"Your store must be approved before publishing products or offers.");return store;}
 private void product(long id,long company){if(db.queryForList("SELECT id FROM products WHERE id=? AND company_id=? FOR UPDATE",id,company).isEmpty())throw fail(HttpStatus.NOT_FOUND,"Product not found.");}
 @PostMapping("/api/auth/business-register") @ResponseStatus(HttpStatus.CREATED) @Transactional
 public AccountService.UserView join(@Valid @RequestBody Join form){
  if(!AccountService.validPassword(form.password())||!form.password().equals(form.confirmPassword()))throw fail(HttpStatus.BAD_REQUEST,"Passwords must match and contain at least 12 characters, up to 72 UTF-8 bytes.");
  String email=AccountService.normalize(form.email());
  if(!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw fail(HttpStatus.BAD_REQUEST,"Use a valid email address.");
  Long user=db.queryForObject("INSERT INTO users(email,password_hash,role) VALUES (?,?,'MERCHANT') RETURNING id",Long.class,email,passwords.encode(form.password()));
  String slug="shop-"+UUID.randomUUID().toString().replace("-","");
  Long company=db.queryForObject("INSERT INTO companies(slug,name,description,monogram,demo,status) VALUES (?,?,?,'M',false,'PENDING') RETURNING id",Long.class,slug,form.businessName().trim(),form.description().trim());
  db.update("INSERT INTO merchant_stores(company_id,user_id,owner_name,business_type,registration_number,phone,contact_email) VALUES (?,?,?,?,?,?,?)",company,user,form.ownerName().trim(),form.businessType(),form.registrationNumber().trim(),form.phone().trim(),email);
  return new AccountService.UserView(user,email,"MERCHANT");
 }
 @GetMapping("/api/merchant/store") public Store mine(@AuthenticationPrincipal AccountService.Principal user){return own(user.id());}
 public record Profile(@NotBlank @Size(max=120) String ownerName,@NotBlank @Size(max=120) String businessName,@NotBlank @Size(max=600) String description,@NotBlank @Size(max=40) String phone){}
 @PutMapping("/api/merchant/store") @Transactional public Store profile(@AuthenticationPrincipal AccountService.Principal user,@Valid @RequestBody Profile form){var s=own(user.id());db.update("UPDATE merchant_stores SET owner_name=?,phone=? WHERE company_id=?",form.ownerName().trim(),form.phone().trim(),s.companyId());db.update("UPDATE companies SET name=?,description=? WHERE id=?",form.businessName().trim(),form.description().trim(),s.companyId());return own(user.id());}
 @GetMapping("/api/admin/stores") public List<Store> all(){return stores(" ORDER BY c.id DESC LIMIT 200");}
 public record Moderation(@NotNull @Pattern(regexp="APPROVED|SUSPENDED") String status,@NotBlank @Size(max=500) String reason){}
 @PatchMapping("/api/admin/stores/{id}") @Transactional public void moderate(@PathVariable long id,@AuthenticationPrincipal AccountService.Principal user,@Valid @RequestBody Moderation form){
  if(db.queryForList("SELECT company_id FROM merchant_stores WHERE company_id=?",id).isEmpty())throw fail(HttpStatus.NOT_FOUND,"Store not found.");
  db.update("UPDATE companies SET status=? WHERE id=?",form.status(),id);db.update("INSERT INTO store_moderation_history(company_id,actor_id,status,reason) VALUES (?,?,?,?)",id,user.id(),form.status(),form.reason().trim());
 }
 @GetMapping("/api/merchant/products") public List<ProductManagement.ProductView> catalog(@AuthenticationPrincipal AccountService.Principal user){long company=own(user.id()).companyId();return db.query("SELECT id FROM products WHERE company_id=? ORDER BY id DESC LIMIT 200",(r,n)->products.detail(r.getLong(1)),company);}
 private AdminCatalogController.ProductData scoped(AdminCatalogController.ProductData p,long company){return new AdminCatalogController.ProductData(p.sku(),p.name(),p.description(),p.category(),p.price(),p.active(),company);}
 @PostMapping("/api/merchant/products") @Transactional public ProductManagement.ProductView create(@AuthenticationPrincipal AccountService.Principal user,@Valid @RequestBody AdminCatalogController.Create form){var s=active(user.id());return products.create(new AdminCatalogController.Create(scoped(form.product(),s.companyId()),form.initialStock()),user.id());}
 @PutMapping("/api/merchant/products/{id}") @Transactional public ProductManagement.ProductView edit(@PathVariable long id,@AuthenticationPrincipal AccountService.Principal user,@Valid @RequestBody AdminCatalogController.Edit form){var s=active(user.id());product(id,s.companyId());return products.edit(id,new AdminCatalogController.Edit(scoped(form.product(),s.companyId()),form.version()));}
 @PatchMapping("/api/merchant/products/{id}/stock") @Transactional public ProductManagement.ProductView stock(@PathVariable long id,@AuthenticationPrincipal AccountService.Principal user,@Valid @RequestBody AdminCatalogController.StockChange form){var s=active(user.id());product(id,s.companyId());return products.adjust(id,form,user.id());}
 @GetMapping("/api/merchant/summary") public Map<String,Object> summary(@AuthenticationPrincipal AccountService.Principal user){long company=own(user.id()).companyId();return Map.of("orders",db.queryForObject("SELECT count(*) FROM merchant_orders WHERE company_id=?",Long.class,company),"demoOrderValue",db.queryForObject("SELECT coalesce(sum(i.unit_price*i.quantity),0) FROM order_items i JOIN orders o ON o.id=i.order_id WHERE i.company_id=? AND o.status<>'CANCELLED'",BigDecimal.class,company),"billingModel","SUBSCRIPTION_AND_COMMISSION","billingStatus","NOT_CONFIGURED","paymentsConnected",false);}
 public record Offer(@NotBlank @Size(max=160) String title,@NotBlank @Size(max=1000) String body,@NotNull Instant startsAt,@NotNull Instant endsAt){}
 @GetMapping("/api/merchant/offers") public List<Map<String,Object>> offers(@AuthenticationPrincipal AccountService.Principal user){return db.queryForList("SELECT id,title,body,starts_at,ends_at FROM store_offers WHERE company_id=? ORDER BY id DESC LIMIT 100",own(user.id()).companyId());}
 @PostMapping("/api/merchant/offers") @Transactional public void offer(@AuthenticationPrincipal AccountService.Principal user,@Valid @RequestBody Offer form){var s=active(user.id());if(!form.endsAt().isAfter(form.startsAt())||!form.endsAt().isAfter(Instant.now()))throw fail(HttpStatus.BAD_REQUEST,"Offer end must follow its start and be in the future.");db.update("INSERT INTO store_offers(company_id,title,body,starts_at,ends_at) VALUES (?,?,?,?,?)",s.companyId(),form.title().trim(),form.body().trim(),java.sql.Timestamp.from(form.startsAt()),java.sql.Timestamp.from(form.endsAt()));}
 @DeleteMapping("/api/merchant/offers/{id}") @Transactional public void deleteOffer(@PathVariable long id,@AuthenticationPrincipal AccountService.Principal user){if(db.update("DELETE FROM store_offers WHERE id=? AND company_id=?",id,own(user.id()).companyId())==0)throw fail(HttpStatus.NOT_FOUND,"Offer not found.");}
 static ResponseStatusException fail(HttpStatus status,String text){return new ResponseStatusException(status,text);}
}
