package com.maramigo;
import com.maramigo.support.BrowserTest;
import com.maramigo.pages.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import static org.junit.jupiter.api.Assertions.*;

/** Actual product photos and sources, synthetic offers, no customer data changes. @author Maram Abbas */
class ManufacturerCatalogTest extends BrowserTest {
 @ParameterizedTest @CsvSource({"1280,900,en","390,844,he","390,844,ar"})
 void realBrandCatalogsHavePhotosSpecificationsAndGuestProtection(int width,int height,String language)throws Exception{
  viewport(width,height);open("#stores");new StorePage(driver,wait).language(language);
  wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".store-card[href='#store/apple']")));
  for(String brand:java.util.List.of("apple","sony","logitech","lego","ikea")){
   open("#store/"+brand);
   wait.until(ExpectedConditions.numberOfElementsToBe(By.cssSelector("[data-testid=product-card]"),4));
   wait.until(d->Boolean.TRUE.equals(js("return [...document.querySelectorAll('.reference-photo')].length===4&&[...document.querySelectorAll('.reference-photo')].every(i=>i.complete&&i.naturalWidth>0)")));
   for(var store:driver.findElements(By.cssSelector(".product-store")))assertTrue(store.getAttribute("href").endsWith("#store/"+brand));
   assertFalse(driver.findElements(By.cssSelector(".manufacturer-notice a[target='_blank']")).isEmpty());
   assertEquals(true,js("return document.documentElement.scrollWidth<=innerWidth"));
  }
  new StorePage(driver,wait).firstDetails();
  wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".reference-specs")));
  assertTrue(driver.findElements(By.cssSelector(".reference-specs>div")).size()>=3);
  assertTrue(driver.findElement(By.cssSelector(".reference-source")).getAttribute("href").startsWith("https://www.ikea.com/"));
  wait.until(d->Boolean.TRUE.equals(js("return document.querySelector('.reference-detail-photo').complete&&document.querySelector('.reference-detail-photo').naturalWidth>0")));
  var folder=java.nio.file.Path.of("target","screenshots");java.nio.file.Files.createDirectories(folder);
  java.nio.file.Files.write(folder.resolve("manufacturer-"+language+"-"+width+".png"),((TakesScreenshot)driver).getScreenshotAs(OutputType.BYTES));
  driver.findElement(By.cssSelector("dialog .close")).sendKeys(Keys.ESCAPE);
  wait.until(ExpectedConditions.numberOfElementsToBe(By.cssSelector("dialog[open]"),0));
  Ui.click(driver,wait,By.cssSelector("[data-testid=product-card] [data-testid=add-to-basket]"));
  wait.until(ExpectedConditions.urlContains("#login"));
 }
}
