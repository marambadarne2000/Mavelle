package com.maramigo.orders;

import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** Consistent demo metrics, grouped by Jerusalem calendar dates. @author Maram Abbas */
@RestController @RequestMapping("/api/admin/dashboard")
public class DashboardController {
 private final JdbcTemplate jdbc;private static final ZoneId ZONE=ZoneId.of("Asia/Jerusalem");
 public DashboardController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public record Day(LocalDate date,long orders,long cancelled,BigDecimal amount){}
 public record Stock(long id,String sku,String name,int stockQuantity){}
 public record Result(LocalDate from,LocalDate to,String timezone,String currency,int threshold,long orders,long cancelled,BigDecimal amount,long lowStockCount,List<Stock> lowStock,List<Day> days,Instant generatedAt){}
 @GetMapping @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Result get(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
   @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
   @RequestParam(defaultValue="5") @Min(0) @Max(100) int threshold){
  LocalDate end=to==null?LocalDate.now(ZONE):to,start=from==null?end.minusDays(29):from;
  if(start.isAfter(end)||java.time.temporal.ChronoUnit.DAYS.between(start,end)>365)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a date range of up to 366 days.");
  Timestamp first=Timestamp.from(start.atStartOfDay(ZONE).toInstant()),last=Timestamp.from(end.plusDays(1).atStartOfDay(ZONE).toInstant());
  var raw=jdbc.query("SELECT (created_at AT TIME ZONE 'Asia/Jerusalem')::date,count(*),count(*) FILTER(WHERE status='CANCELLED'),COALESCE(sum(total_price) FILTER(WHERE status<>'CANCELLED'),0) FROM orders WHERE created_at>=? AND created_at<? GROUP BY 1 ORDER BY 1",
    (r,n)->new Day(r.getDate(1).toLocalDate(),r.getLong(2),r.getLong(3),r.getBigDecimal(4)),first,last);
  Map<LocalDate,Day> indexed=new HashMap<>();raw.forEach(d->indexed.put(d.date(),d));List<Day> days=new ArrayList<>();
  for(LocalDate d=start;!d.isAfter(end);d=d.plusDays(1))days.add(indexed.getOrDefault(d,new Day(d,0,0,BigDecimal.ZERO)));
  var stocks=jdbc.query("SELECT id,sku,name,stock_quantity FROM products WHERE active=true AND stock_quantity<=? ORDER BY stock_quantity,id LIMIT 20",(r,n)->new Stock(r.getLong(1),r.getString(2),r.getString(3),r.getInt(4)),threshold);
  long low=jdbc.queryForObject("SELECT count(*) FROM products WHERE active=true AND stock_quantity<=?",Long.class,threshold);
  return new Result(start,end,ZONE.getId(),"ILS",threshold,raw.stream().mapToLong(Day::orders).sum(),raw.stream().mapToLong(Day::cancelled).sum(),raw.stream().map(Day::amount).reduce(BigDecimal.ZERO,BigDecimal::add),low,stocks,days,Instant.now());
 }
}
