package com.maramigo;

import com.maramigo.support.*;import com.maramigo.pages.*;
import org.junit.jupiter.api.*;import org.junit.jupiter.params.ParameterizedTest;import org.junit.jupiter.params.provider.ValueSource;
import org.openqa.selenium.*;import org.openqa.selenium.support.ui.*;
import java.time.Duration;import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** Runs only in a disposable database schema and separate application. @author Maram Abbas */
@Tag("purchase")
class PurchaseE2ETest extends BrowserTest {
 private FixtureApi api;
 @BeforeEach void fixture()throws Exception{api=new FixtureApi(base);}
 private void english(WebDriver browser,WebDriverWait waiting){waiting.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".language-picker select")));new Select(browser.findElement(By.cssSelector(".language-picker select"))).selectByValue("en");waiting.until(d->"en".equals(d.findElement(By.tagName("html")).getAttribute("lang")));}
 private void login(WebDriver browser,WebDriverWait waiting,String email,String password){boolean owner=email.equals(FixtureApi.adminEmail());browser.get(base+(owner?"/#owner-login":"/#login"));english(browser,waiting);new LoginPage(browser,waiting).submit(email,password);waiting.until(ExpectedConditions.urlToBe(base+(owner?"/#marketplace-admin":"/#account")));}
 private void selectProduct(FixtureApi.Product product){open("#collection");english(driver,wait);var store=new StorePage(driver,wait);store.ready();store.search(product.name());store.onlyProduct(product.name());}
 private void add(){Ui.click(driver,wait,By.cssSelector("[data-testid=product-card] [data-testid=add-to-basket]"));}
 private void basket(){Ui.click(driver,wait,Ui.id("basket-link"));new BasketPage(driver,wait).ready();}
 private String buy(){basket();new BasketPage(driver,wait).checkout();var checkout=new CheckoutPage(driver,wait);checkout.shipping();return checkout.confirm();}
 private void screenshot(String name)throws Exception{Path dir=Path.of("target","screenshots");Files.createDirectories(dir);Files.write(dir.resolve(name+".png"),((TakesScreenshot)driver).getScreenshotAs(OutputType.BYTES));}

 @ParameterizedTest @ValueSource(ints={1280,390})
 void signupLoginBasketCheckoutAndLogout(int width)throws Exception{
  viewport(width,900);var product=api.product(4);String email="signup-"+java.util.UUID.randomUUID()+"@example.test";
  open("#register");english(driver,wait);new LoginPage(driver,wait).submit(email,FixtureApi.PASSWORD);wait.until(ExpectedConditions.urlContains("#login"));
  open("#basket");wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".basket-empty")));
  selectProduct(product);add();wait.until(ExpectedConditions.urlContains("#login"));assertTrue(driver.findElement(Ui.id("basket-link")).getText().contains("(0)"));
  new LoginPage(driver,wait).submit(email,FixtureApi.PASSWORD);wait.until(ExpectedConditions.urlContains("#collection"));wait.until(ExpectedConditions.textToBePresentInElementLocated(Ui.id("basket-link"),"(1)"));
  basket();var basket=new BasketPage(driver,wait);basket.quantity(product.id(),2);basket.subtotal("20.00");driver.navigate().refresh();basket.ready();assertEquals("2",driver.findElement(By.id("quantity-"+product.id())).getAttribute("value"));
  basket.checkout();var checkout=new CheckoutPage(driver,wait);checkout.requiredFieldsPreventSubmit();checkout.shipping();String number=checkout.confirm();assertTrue(number.startsWith("MG-"));assertEquals(2,api.stock(product));
  assertTrue(driver.findElement(Ui.id("basket-link")).getText().contains("(0)"));assertEquals(true,js("return document.documentElement.scrollWidth<=innerWidth"));
  var stored=api.get("/api/admin/orders/"+number).get("order");assertEquals(0,stored.get("total").decimalValue().compareTo(new java.math.BigDecimal("20.00")));assertEquals(product.sku(),stored.get("items").get(0).get("sku").asText());
  driver.navigate().refresh();wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".order-confirmation")));screenshot("checkout-"+width);
  open("#orders");wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("a[href='#order/"+number+"']")));
  open("#account");Ui.click(driver,wait,Ui.id("logout"));wait.until(ExpectedConditions.urlContains("#home"));open("#orders");wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("main a[href='#login']")));assertTrue(driver.findElements(By.cssSelector(".order-list")).isEmpty());
 }
 @Test void lastUnitCannotBeAddedFromAStaleSecondBrowser()throws Exception{
  var product=api.product(1);String a=api.customer(),b=api.customer();var other=createBrowser();var otherWait=new WebDriverWait(other,Duration.ofSeconds(15));
  try{
   login(driver,wait,a,FixtureApi.PASSWORD);selectProduct(product);add();wait.until(ExpectedConditions.textToBePresentInElementLocated(Ui.id("basket-link"),"(1)"));
   login(other,otherWait,b,FixtureApi.PASSWORD);other.get(base+"/#collection");var otherStore=new StorePage(other,otherWait);otherStore.ready();otherStore.search(product.name());otherStore.onlyProduct(product.name());
   buy();assertEquals(0,api.stock(product));Ui.click(other,otherWait,By.cssSelector("[data-testid=product-card] [data-testid=add-to-basket]"));
   otherWait.until(ExpectedConditions.textToBePresentInElementLocated(By.cssSelector(".basket-feedback"),"stock"));assertTrue(other.findElement(Ui.id("basket-link")).getText().contains("(0)"));
  }finally{other.quit();}
 }
 @Test void changedPriceIsReviewedAndRecordedAtCheckout()throws Exception{
  var product=api.product(3);login(driver,wait,api.customer(),FixtureApi.PASSWORD);selectProduct(product);add();basket();var basket=new BasketPage(driver,wait);basket.subtotal("10.00");api.price(product,"12.50");basket.refresh();basket.subtotal("12.50");wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".basket-price-update")));
  basket.checkout();var checkout=new CheckoutPage(driver,wait);checkout.shipping();String number=checkout.confirm();assertEquals(0,api.get("/api/admin/orders/"+number).get("order").get("items").get(0).get("unitPrice").decimalValue().compareTo(new java.math.BigDecimal("12.50")));
  api.price(product,"99.00");driver.navigate().refresh();wait.until(ExpectedConditions.textToBePresentInElementLocated(By.cssSelector(".order-confirmation .checkout-total strong"),"12.50"));
 }
 @Test void aNewIdenticalPurchaseGetsANewOrderAndDeductsStockAgain()throws Exception{
  var product=api.product(3);login(driver,wait,api.customer(),FixtureApi.PASSWORD);selectProduct(product);add();wait.until(ExpectedConditions.textToBePresentInElementLocated(Ui.id("basket-link"),"(1)"));String first=buy();
  assertEquals(null,js("return sessionStorage.getItem('maramigo.checkout')"));assertEquals(2,api.stock(product));
  selectProduct(product);add();wait.until(ExpectedConditions.textToBePresentInElementLocated(Ui.id("basket-link"),"(1)"));String second=buy();assertNotEquals(first,second);assertEquals(1,api.stock(product));
  open("#orders");wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("a[href='#order/"+first+"']")));assertFalse(driver.findElements(By.cssSelector("a[href='#order/"+second+"']")).isEmpty());
 }
 @Test void soldOutAndDeactivatedProductsBlockCheckout()throws Exception{
  var product=api.product(2);login(driver,wait,api.customer(),FixtureApi.PASSWORD);selectProduct(product);add();basket();api.stock(product,0);new BasketPage(driver,wait).refresh();wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".basket-item-issue")));assertTrue(driver.findElements(By.cssSelector("a[href='#checkout']")).isEmpty());
  api.inactive(product);new BasketPage(driver,wait).refresh();wait.until(ExpectedConditions.textToBePresentInElementLocated(By.cssSelector(".basket-item h2"),"Unavailable"));Ui.click(driver,wait,Ui.id("cart-remove"));wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".basket-empty")));
 }
 @Test void administratorSeesCustomerOrderAndCancellationRestoresStock()throws Exception{
  var product=api.product(2);login(driver,wait,api.customer(),FixtureApi.PASSWORD);selectProduct(product);add();var manager=createBrowser();var managerWait=new WebDriverWait(manager,Duration.ofSeconds(15));
  try{
   login(manager,managerWait,FixtureApi.adminEmail(),FixtureApi.adminPassword());manager.get(base+"/#admin-orders");managerWait.until(ExpectedConditions.visibilityOfElementLocated(Ui.id("admin-order-search")));String number=buy();Ui.click(manager,managerWait,Ui.id("admin-orders-refresh"));Ui.fill(manager,managerWait,Ui.id("admin-order-search"),number);Ui.click(manager,managerWait,By.cssSelector("[data-testid=admin-order-row][data-order-number='"+number+"']"));managerWait.until(ExpectedConditions.visibilityOfElementLocated(Ui.id("order-status-form")));
   new Select(manager.findElement(By.cssSelector("[data-testid=order-status-form] select"))).selectByValue("CANCELLED");Ui.fill(manager,managerWait,By.cssSelector("[data-testid=order-status-form] input:not([type=checkbox])"),"Isolated two-browser cancellation");Ui.click(manager,managerWait,Ui.id("cancel-confirm"));Ui.click(manager,managerWait,Ui.id("order-status-submit"));managerWait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".order-admin-detail [data-status=CANCELLED]")));
   assertEquals(2,api.stock(product));driver.navigate().refresh();wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".order-confirmation [data-status=CANCELLED]")));screenshot("customer-cancelled-order");
  }finally{manager.quit();}
 }
}
