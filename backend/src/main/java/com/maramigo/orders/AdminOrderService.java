package com.maramigo.orders;

import com.maramigo.catalog.ProductRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;

/** Locked transitions, cancellation and stock restoration share one transaction. @author Maram Abbas */
@Service @Transactional(readOnly=true)
public class AdminOrderService {
    private final JdbcTemplate jdbc;private final ProductRepository products;private final OrderService orders;
    public AdminOrderService(JdbcTemplate jdbc,ProductRepository products,OrderService orders){this.jdbc=jdbc;this.products=products;this.orders=orders;}
    public record Page(List<OrderService.Summary> items,long total,int page,int size){}
    public record History(String fromStatus,String toStatus,String reason,long actorId,Instant occurredAt){}
    public record Detail(OrderService.View order,long version,List<History> history){}
    private record Header(long id,long userId,long version,String status){}
    public Page list(String status,String search,int page){
        // strpos treats % and _ as literal characters rather than SQL wildcards.
        String filter=" FROM orders WHERE (?='' OR status=?) AND strpos(lower(order_number),lower(?))>0";
        var items=jdbc.query("SELECT order_number,status,total_price,currency,created_at"+filter+" ORDER BY created_at DESC,id DESC LIMIT 20 OFFSET ?",
            (r,n)->new OrderService.Summary(r.getString(1),r.getString(2),r.getBigDecimal(3),r.getString(4),r.getTimestamp(5).toInstant()),status,status,search,(long)page*20);
        long total=jdbc.queryForObject("SELECT count(*)"+filter,Long.class,status,status,search);
        return new Page(items,total,page,20);
    }
    private Header header(String number,boolean lock){
        var rows=jdbc.query("SELECT id,user_id,version,status FROM orders WHERE order_number=?"+(lock?" FOR UPDATE":""),
            (r,n)->new Header(r.getLong(1),r.getLong(2),r.getLong(3),r.getString(4)),number);
        if(rows.isEmpty())throw fail(HttpStatus.NOT_FOUND,"Order not found.");return rows.get(0);
    }
    public Detail detail(String number){return detail(number,header(number,false));}
    private Detail detail(String number,Header row){
        var history=jdbc.query("SELECT from_status,to_status,reason,actor_id,occurred_at FROM order_status_history WHERE order_id=? ORDER BY id",
            (r,n)->new History(r.getString(1),r.getString(2),r.getString(3),r.getLong(4),r.getTimestamp(5).toInstant()),row.id());
        return new Detail(orders.view(number,row.userId()),row.version(),history);
    }
    @Transactional public Detail change(String number,AdminOrderController.Change change,long actorId){
        var row=header(number,true);
        // A repeated completed cancellation is safe even when the original response was lost.
        if(row.status().equals("CANCELLED") && change.status().equals("CANCELLED"))return detail(number,row);
        if(row.version()!=change.version())throw fail(HttpStatus.CONFLICT,"This order changed. Reload before updating it.");
        boolean allowed=(row.status().equals("PENDING") && (change.status().equals("SHIPPED") || change.status().equals("CANCELLED")))
            || (row.status().equals("SHIPPED") && change.status().equals("DELIVERED"));
        if(!allowed)throw fail(HttpStatus.CONFLICT,"This order status transition is not allowed.");
        if(change.status().equals("CANCELLED")){
            var items=jdbc.query("SELECT product_id,quantity FROM order_items WHERE order_id=? ORDER BY product_id",(r,n)->new long[]{r.getLong(1),r.getLong(2)},row.id());
            for(var item:items){
                var product=products.findLockedById(item[0]).orElseThrow();
                if((long)product.getStockQuantity()+item[1]>1000000)throw fail(HttpStatus.CONFLICT,"Restoring stock would exceed the inventory limit.");
                product.adjustStock((int)item[1]);
                jdbc.update("INSERT INTO inventory_movements(product_id,actor_id,quantity_delta,reason,order_id) VALUES (?,?,?,?,?)",item[0],actorId,item[1],"Cancelled "+number,row.id());
            }
            products.flush();
        }
        jdbc.update("UPDATE orders SET status=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",change.status(),row.id());
        jdbc.update("INSERT INTO order_status_history(order_id,actor_id,from_status,to_status,reason) VALUES (?,?,?,?,?)",row.id(),actorId,row.status(),change.status(),change.reason());
        return detail(number,new Header(row.id(),row.userId(),row.version()+1,change.status()));
    }
    private ResponseStatusException fail(HttpStatus status,String message){return new ResponseStatusException(status,message);}
}
