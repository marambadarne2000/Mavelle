package com.maramigo.catalog;

import com.maramigo.accounts.AccountService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

/** Bounded decoded/re-encoded raster media; no SVG, HTML, remote fetch or file paths. @author Maram Abbas */
@RestController
public class MediaController {
 private final JdbcTemplate db;public MediaController(JdbcTemplate db){this.db=db;}
 private record Target(String table,String column,long company){}
 private Target target(String kind,long id,AccountService.Principal user){
  if(!Set.of("products","companies").contains(kind))throw fail(HttpStatus.NOT_FOUND,"Media target not found.");
  var companies=kind.equals("products")?db.queryForList("SELECT company_id FROM products WHERE id=? FOR UPDATE",Long.class,id):db.queryForList("SELECT id FROM companies WHERE id=? FOR UPDATE",Long.class,id);
  if(companies.isEmpty())throw fail(HttpStatus.NOT_FOUND,"Media target not found.");long company=companies.get(0);
  if(!user.role().equals("ADMIN")&&(!user.role().equals("MERCHANT")||!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM merchant_stores WHERE company_id=? AND user_id=?)",Boolean.class,company,user.id()))))throw fail(HttpStatus.FORBIDDEN,"This media belongs to another store.");
  return new Target(kind,kind.equals("products")?"image_id":"logo_id",company);
 }
 static byte[] normalize(MultipartFile file){
  if(file.isEmpty()||file.getSize()>2_000_000)throw fail(HttpStatus.BAD_REQUEST,"Choose a PNG or JPEG image up to 2 MB and 12 megapixels.");
  try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(file.getBytes()))){var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException();var reader=readers.next();try{reader.setInput(input);String format=reader.getFormatName().toLowerCase(Locale.ROOT);int width=reader.getWidth(0),height=reader.getHeight(0);if(!Set.of("png","jpeg","jpg").contains(format)||width<1||height<1||((long)width*height)>12_000_000)throw new IOException();BufferedImage source=reader.read(0);double scale=Math.min(1,1200.0/Math.max(width,height));var clean=new BufferedImage(Math.max(1,(int)(width*scale)),Math.max(1,(int)(height*scale)),BufferedImage.TYPE_INT_ARGB);var graphics=clean.createGraphics();try{graphics.drawImage(source,0,0,clean.getWidth(),clean.getHeight(),null);}finally{graphics.dispose();}var out=new ByteArrayOutputStream();ImageIO.write(clean,"png",out);if(out.size()>3_000_000)throw new IOException();return out.toByteArray();}finally{reader.dispose();}}
  catch(Exception invalid){throw fail(HttpStatus.BAD_REQUEST,"Choose a PNG or JPEG image up to 2 MB and 12 megapixels.");}
 }
 @PostMapping("/api/media/{kind}/{id}") @Transactional public Map<String,String> upload(@PathVariable String kind,@PathVariable long id,@RequestParam MultipartFile file,@AuthenticationPrincipal AccountService.Principal user){var target=target(kind,id,user);byte[] bytes=normalize(file);UUID asset=UUID.randomUUID();db.update("INSERT INTO media_assets(id,owner_id,content) VALUES (?,?,?)",asset,user.id(),bytes);var previous=db.queryForObject("SELECT "+target.column()+" FROM "+target.table()+" WHERE id=?",UUID.class,id);db.update("UPDATE "+target.table()+" SET "+target.column()+"=?"+(kind.equals("products")?",version=version+1":"")+" WHERE id=?",asset,id);if(previous!=null)db.update("DELETE FROM media_assets WHERE id=?",previous);return Map.of("id",asset.toString());}
 @DeleteMapping("/api/media/{kind}/{id}") @Transactional public void remove(@PathVariable String kind,@PathVariable long id,@AuthenticationPrincipal AccountService.Principal user){var target=target(kind,id,user);var previous=db.queryForObject("SELECT "+target.column()+" FROM "+target.table()+" WHERE id=?",UUID.class,id);db.update("UPDATE "+target.table()+" SET "+target.column()+"=NULL"+(kind.equals("products")?",version=version+1":"")+" WHERE id=?",id);if(previous!=null)db.update("DELETE FROM media_assets WHERE id=?",previous);}
 @GetMapping("/api/media/{asset}") public ResponseEntity<byte[]> image(@PathVariable UUID asset){var rows=db.queryForList("SELECT a.content FROM media_assets a WHERE a.id=? AND (EXISTS(SELECT 1 FROM products p JOIN companies c ON c.id=p.company_id WHERE p.image_id=a.id AND p.active AND c.status='APPROVED') OR EXISTS(SELECT 1 FROM companies c WHERE c.logo_id=a.id AND c.status='APPROVED'))",byte[].class,asset);if(rows.isEmpty())throw fail(HttpStatus.NOT_FOUND,"Image not found.");return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(rows.get(0));}
 private static ResponseStatusException fail(HttpStatus status,String text){return new ResponseStatusException(status,text);}
}
