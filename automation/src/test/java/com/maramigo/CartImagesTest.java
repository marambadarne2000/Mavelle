package com.maramigo;
import com.maramigo.support.BrowserTest;
import com.maramigo.pages.StorePage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Fresh browser-only basket fixtures; no account, order or inventory writes. */
class CartImagesTest extends BrowserTest {
 @ParameterizedTest @CsvSource({"1280,1100,en","390,844,he","390,844,ar"})
 void differentProductsKeepTheirOwnLoadedBasketPhotos(int width,int height,String language)throws Exception{
  viewport(width,height);var json=new ObjectMapper();
  var response=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(java.time.Duration.ofSeconds(10)).build().send(HttpRequest.newBuilder(URI.create(base+"/api/products?size=48")).timeout(java.time.Duration.ofSeconds(20)).GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,response.statusCode());
  var products=new HashMap<String,JsonNode>();json.readTree(response.body()).get("items").forEach(p->products.put(p.get("sku").asText(),p));
  var selected=List.of(products.get("REF-AP-IPAD10"),products.get("REF-AP-AIRPODS2"),products.get("REF-SO-WH-CH720N"),products.get("MG-001"));
  open("#home");new StorePage(driver,wait).language(language);
  var items=selected.stream().map(p->Map.of("productId",p.get("id").asLong(),"quantity",1)).toList();
  js("localStorage.setItem('maramigo.cart.v1',arguments[0])",json.writeValueAsString(Map.of("version",1,"items",items)));open("#basket");driver.navigate().refresh();
  wait.until(ExpectedConditions.numberOfElementsToBe(By.cssSelector("[data-testid=basket-item]"),4));
  for(var product:selected.subList(0,3)){
   var row=driver.findElement(By.cssSelector("[data-testid=basket-item]:has([data-product-id='"+product.get("id").asLong()+"'])"));
   js("arguments[0].scrollIntoView({block:'center',behavior:'instant'})",row);
   var image=row.findElement(By.cssSelector(".basket-product-photo"));
   wait.until(d->Boolean.TRUE.equals(js("return arguments[0].complete&&arguments[0].naturalWidth>0",image)));
   assertEquals(product.get("referenceImageUrl").asText(),URI.create(image.getAttribute("src")).getPath());assertEquals("contain",image.getCssValue("object-fit"));
  }
  var noPhoto=driver.findElement(By.cssSelector("[data-testid=basket-item]:has([data-product-id='"+selected.get(3).get("id").asLong()+"'])"));
  assertEquals(0,noPhoto.findElements(By.tagName("img")).size());assertEquals(1,noPhoto.findElements(By.cssSelector(".art-living .product-shape[role=img]")).size());
  assertEquals(1,driver.findElements(By.cssSelector(".basket-art .product-shape")).size());assertEquals(true,js("return document.documentElement.scrollWidth<=innerWidth"));
  js("document.querySelector('.basket-items').scrollIntoView({block:'start',behavior:'instant'})");
  var folder=Path.of("target","screenshots");Files.createDirectories(folder);Files.write(folder.resolve("basket-product-photos-"+language+"-"+width+".png"),((TakesScreenshot)driver).getScreenshotAs(OutputType.BYTES));
 }
}
