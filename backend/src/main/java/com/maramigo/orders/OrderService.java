package com.maramigo.orders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maramigo.catalog.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Atomic checkout, stable snapshots and per-user idempotency. @author Maram Abbas */
@Service @Transactional(readOnly=true)
public class OrderService {
    private final JdbcTemplate jdbc; private final ProductRepository products; private final CompanyRepository companies; private final ObjectMapper json;
    public OrderService(JdbcTemplate jdbc,ProductRepository products,CompanyRepository companies,ObjectMapper json){this.jdbc=jdbc;this.products=products;this.companies=companies;this.json=json;}
    public record Item(Long productId,String sku,String name,String companyName,BigDecimal unitPrice,int quantity,BigDecimal lineTotal){}
    public record Summary(String orderNumber,String status,BigDecimal total,String currency,Instant createdAt){}
    public record View(String orderNumber,String status,BigDecimal total,String currency,Instant createdAt,OrderController.Shipping shipping,List<Item> items){}
    private record Stored(long id,long userId,String fingerprint,View view){}
    @Transactional public View checkout(Long userId,String key,OrderController.Checkout request){
        var lines=request.items().stream().sorted(Comparator.comparing(OrderController.Line::productId)).toList();
        if(lines.stream().map(OrderController.Line::productId).distinct().count()!=lines.size())throw fail(HttpStatus.BAD_REQUEST,"Duplicate products are not allowed.");
        String fingerprint=fingerprint(lines,request.shipping());
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",userId+":"+key);
        var existing=jdbc.queryForList("SELECT order_number,request_fingerprint FROM orders WHERE user_id=? AND idempotency_key=?",userId,key);
        if(!existing.isEmpty()){
            if(!fingerprint.equals(existing.get(0).get("request_fingerprint")))throw fail(HttpStatus.CONFLICT,"This request key was already used for different order details.");
            return view((String)existing.get(0).get("order_number"),userId);
        }
        List<Product> locked=new ArrayList<>(); BigDecimal total=BigDecimal.ZERO;
        for(var line:lines){
            var p=products.findLockedById(line.productId()).orElseThrow(()->fail(HttpStatus.CONFLICT,"A product is no longer available. Refresh your basket."));
            if(!p.isActive())throw fail(HttpStatus.CONFLICT,"A product is no longer available. Refresh your basket.");
            if(p.getPrice().compareTo(line.expectedPrice())!=0)throw fail(HttpStatus.CONFLICT,"Prices changed. Refresh your basket and confirm the updated prices.");
            if(p.getStockQuantity()<line.quantity())throw fail(HttpStatus.CONFLICT,"There is not enough stock. Refresh your basket.");
            total=total.add(p.getPrice().multiply(BigDecimal.valueOf(line.quantity()))); locked.add(p);
        }
        String number="MG-"+UUID.randomUUID().toString().replace("-","");
        Long id=jdbc.queryForObject("INSERT INTO orders(order_number,user_id,total_price,status,shipping,idempotency_key,request_fingerprint) VALUES (?,?,?,'PENDING',?::jsonb,?,?) RETURNING id",Long.class,number,userId,total,encode(request.shipping()),key,fingerprint);
        for(int i=0;i<lines.size();i++){
            var line=lines.get(i);var p=locked.get(i);
            String company=companies.findById(p.getCompanyId()).orElseThrow().getName();
            jdbc.update("INSERT INTO order_items(order_id,product_id,sku,name,company_name,unit_price,quantity) VALUES (?,?,?,?,?,?,?)",id,p.getId(),p.getSku(),p.getName(),company,p.getPrice(),line.quantity());
            p.adjustStock(-line.quantity());
            jdbc.update("INSERT INTO inventory_movements(product_id,actor_id,quantity_delta,reason,order_id) VALUES (?,?,?,?,?)",p.getId(),userId,-line.quantity(),"Order "+number,id);
        }
        products.flush();
        return view(number,userId);
    }
    public List<Summary> list(Long userId,int page){return jdbc.query("SELECT order_number,status,total_price,currency,created_at FROM orders WHERE user_id=? ORDER BY created_at DESC,id DESC LIMIT 20 OFFSET ?",(r,n)->new Summary(r.getString(1),r.getString(2),r.getBigDecimal(3),r.getString(4),r.getTimestamp(5).toInstant()),userId,(long)page*20);}
    public View view(String number,Long userId){
        var rows=jdbc.query("SELECT id,user_id,request_fingerprint,order_number,status,total_price,currency,created_at,shipping::text FROM orders WHERE order_number=? AND user_id=?",(r,n)->new Stored(r.getLong(1),r.getLong(2),r.getString(3),new View(r.getString(4),r.getString(5),r.getBigDecimal(6),r.getString(7),r.getTimestamp(8).toInstant(),decode(r.getString(9)),List.of())),number,userId);
        if(rows.isEmpty())throw fail(HttpStatus.NOT_FOUND,"Order not found.");
        var row=rows.get(0);var v=row.view();
        var items=jdbc.query("SELECT product_id,sku,name,company_name,unit_price,quantity FROM order_items WHERE order_id=? ORDER BY id",(r,n)->new Item(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getBigDecimal(5),r.getInt(6),r.getBigDecimal(5).multiply(BigDecimal.valueOf(r.getInt(6)))),row.id());
        return new View(v.orderNumber(),v.status(),v.total(),v.currency(),v.createdAt(),v.shipping(),items);
    }
    private String fingerprint(List<OrderController.Line> lines,OrderController.Shipping shipping){
        try {var canonical=new TreeMap<String,Object>();canonical.put("shipping",shipping);canonical.put("items",lines.stream().map(l->List.of(l.productId(),l.quantity(),l.expectedPrice().setScale(2).toPlainString())).toList());return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode(canonical).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("Could not prepare order request.",e);}
    }
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("Could not serialize order.",e);}}
    private OrderController.Shipping decode(String value){try{return json.readValue(value,OrderController.Shipping.class);}catch(Exception e){throw new IllegalStateException("Could not read order.",e);}}
    private ResponseStatusException fail(HttpStatus status,String message){return new ResponseStatusException(status,message);}
}
