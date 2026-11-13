package com.maramigo.marketplace;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;
/** Public advertisements are approved stores' active announcements, never private buyer data. @author Maram Abbas */
@RestController
public class DiscoveryController {
 private final JdbcTemplate db;public DiscoveryController(JdbcTemplate db){this.db=db;}
 public record Announcement(long id,String title,String body,long companyId,String companyName,String slug,boolean demo,Instant endsAt){}
 @GetMapping("/api/discovery/announcements") public List<Announcement> announcements(){return db.query("SELECT f.id,f.title,f.body,c.id,c.name,c.slug,c.demo,f.ends_at FROM store_offers f JOIN companies c ON c.id=f.company_id WHERE c.status='APPROVED' AND f.public_advertisement=true AND f.starts_at<=CURRENT_TIMESTAMP AND f.ends_at>CURRENT_TIMESTAMP ORDER BY f.created_at DESC,f.id DESC LIMIT 12",(r,n)->new Announcement(r.getLong(1),r.getString(2),r.getString(3),r.getLong(4),r.getString(5),r.getString(6),r.getBoolean(7),r.getTimestamp(8).toInstant()));}
}
