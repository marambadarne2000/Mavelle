package com.maramigo;
import com.maramigo.support.BrowserTest;
import com.maramigo.pages.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import static org.junit.jupiter.api.Assertions.*;

/** Read-only journeys use a fresh browser and never change customer orders. @author Maram Abbas */
class StorefrontTest extends BrowserTest {
 @ParameterizedTest @CsvSource({"1280,900,en,SILK,The Silk Pillow","390,844,he,כרית,כרית המשי","390,844,ar,وسادة الحرير,وسادة الحرير"})
 void catalogSearchDialogAndResponsiveLayout(int width,int height,String language,String query,String name){
  viewport(width,height);open("#collection");var store=new StorePage(driver,wait);store.ready();store.language(language);store.search(query);store.onlyProduct(name);assertEquals((long)width,js("return window.innerWidth"));
  assertEquals(language.equals("en")?"ltr":"rtl",driver.findElement(By.tagName("html")).getAttribute("dir"));
  assertEquals(true,js("return matchMedia('(prefers-reduced-motion: reduce)').matches"));
  assertEquals(true,js("return document.documentElement.scrollWidth <= window.innerWidth"));store.firstDetails();
  var close=driver.findElement(By.cssSelector("dialog .close"));wait.until(d->close.equals(d.switchTo().activeElement()));close.sendKeys(Keys.chord(Keys.SHIFT,Keys.TAB));
  assertEquals(true,js("return !!document.activeElement.closest('dialog')"));driver.switchTo().activeElement().sendKeys(Keys.TAB);assertEquals(close,driver.switchTo().activeElement());
  close.sendKeys(Keys.ESCAPE);wait.until(ExpectedConditions.numberOfElementsToBe(By.cssSelector("dialog[open]"),0));assertEquals("button",driver.switchTo().activeElement().getTagName());
  try{var folder=java.nio.file.Path.of("target","screenshots");java.nio.file.Files.createDirectories(folder);java.nio.file.Files.write(folder.resolve("catalog-"+language+"-"+width+".png"),((TakesScreenshot)driver).getScreenshotAs(OutputType.BYTES));}catch(java.io.IOException e){throw new IllegalStateException("Could not save browser evidence",e);}
 }
 @Test void companyCatalogIsSeparateAndUnknownRoutesRecover(){
  open("#store/noir-atelier");var store=new StorePage(driver,wait);store.ready();wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".store-hero h1")));
  assertEquals("Noir Atelier",driver.findElement(By.cssSelector(".store-hero h1")).getText());
  for(var link:driver.findElements(By.cssSelector(".product-store")))assertTrue(link.getAttribute("href").endsWith("#store/noir-atelier"));
  open("#missing-page");wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("main h1")));assertEquals(1,driver.findElements(By.tagName("main")).size());
  driver.findElement(By.cssSelector("main a[href='#home']")).click();wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".hero h1")));
 }
 @Test void invalidLoginIsFocusedAndFormRemainsUsable(){
  open("#login");var login=new LoginPage(driver,wait);login.submit("invalid-"+java.util.UUID.randomUUID()+"@example.test","Synthetic-invalid-password");var error=login.error();assertEquals(error,driver.switchTo().activeElement());assertTrue(driver.findElement(By.cssSelector("[data-testid=auth-submit]")).isEnabled());
  new StorePage(driver,wait).language("en");assertTrue(driver.findElements(Ui.id("owner-entry")).isEmpty());assertTrue(driver.findElements(Ui.id("staff-entry")).isEmpty());
  Ui.click(driver,wait,Ui.id("business-entry"));wait.until(ExpectedConditions.urlToBe(base+"/#join-business"));wait.until(ExpectedConditions.visibilityOfElementLocated(Ui.id("business-registration")));
  open("#login");Ui.click(driver,wait,By.cssSelector("a[href='#staff-register']"));wait.until(ExpectedConditions.visibilityOfElementLocated(Ui.id("staff-registration")));


 }
 @Test void skipNavigationWorksWithoutChangingRoute(){
  open("#home");wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".hero h1")));driver.switchTo().activeElement().sendKeys(Keys.TAB);var skip=driver.findElement(By.cssSelector("[data-testid=skip-content]"));assertEquals(skip,driver.switchTo().activeElement());skip.sendKeys(Keys.ENTER);assertEquals("page-content",driver.switchTo().activeElement().getAttribute("id"));assertTrue(driver.getCurrentUrl().endsWith("#home"));
 }
}
