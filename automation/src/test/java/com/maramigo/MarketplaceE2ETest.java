package com.maramigo;
import com.maramigo.support.*;
import com.maramigo.pages.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;
import java.util.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real onboarding, private catalog, customer favorites and per-store fulfilment. @author Maram Abbas */
@Tag("purchase")
class MarketplaceE2ETest extends BrowserTest {
 private void click(String text){Ui.click(driver,wait,By.xpath("//main//button[normalize-space()='"+text+"']"));}
 private void login(String email,String password,String destination){open("#login");new StorePage(driver,wait).language("en");new LoginPage(driver,wait).submit(email,password);wait.until(ExpectedConditions.urlToBe(base+"/"+destination));}
 private void logout(){open("#account");Ui.click(driver,wait,Ui.id("logout"));wait.until(ExpectedConditions.urlContains("#home"));}
 private void evidence(String name)throws Exception{var folder=Path.of("target","screenshots");Files.createDirectories(folder);Files.write(folder.resolve(name+".png"),((TakesScreenshot)driver).getScreenshotAs(OutputType.BYTES));}
 private void audit(String name)throws Exception{
  js(Files.readString(Path.of("..","frontend","node_modules","axe-core","axe.min.js")));driver.manage().timeouts().scriptTimeout(java.time.Duration.ofSeconds(45));
  String result=(String)((JavascriptExecutor)driver).executeAsyncScript("const done=arguments[arguments.length-1];axe.run(document,{runOnly:{type:'tag',values:['wcag2a','wcag2aa','wcag21aa','wcag22aa','best-practice']},resultTypes:['violations','incomplete']}).then(r=>done(JSON.stringify({violations:r.violations,incomplete:r.incomplete}))).catch(e=>done(JSON.stringify({error:e.message})))");
  var json=new com.fasterxml.jackson.databind.ObjectMapper();var report=json.readTree(result);var folder=Path.of("target","accessibility");Files.createDirectories(folder);Files.writeString(folder.resolve("marketplace-"+name+".json"),result);assertFalse(report.has("error"));assertEquals(0,report.get("violations").size(),report.get("violations").toString());
 }
 @ParameterizedTest @ValueSource(ints={1280,390})
 void merchantJoinsThenCustomerShopsAndTracksStoreDelivery(int width)throws Exception{
  viewport(width,900);var api=new FixtureApi(base);String suffix=UUID.randomUUID().toString().substring(0,8),email="merchant-"+suffix+"@example.test",name="Synthetic store "+suffix,product="Marketplace item "+suffix,sku="MARKET-"+suffix.toUpperCase(Locale.ROOT);
  open("#join-business");new StorePage(driver,wait).language("en");wait.until(ExpectedConditions.visibilityOfElementLocated(Ui.id("business-registration")));
  for(var field:Map.of("ownerName","Synthetic Merchant","businessName",name,"phone","0500000000","email",email,"password",FixtureApi.PASSWORD,"confirmPassword",FixtureApi.PASSWORD).entrySet())Ui.fill(driver,wait,Ui.id("join-"+field.getKey()),field.getValue());
  Ui.fill(driver,wait,By.cssSelector("[data-testid=business-registration] textarea"),"Synthetic browser store");Ui.click(driver,wait,By.cssSelector("[data-testid=business-registration] input[type=checkbox]"));Ui.click(driver,wait,By.cssSelector("[data-testid=business-registration] button[type=submit]"));
  wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("main"),"Your application was received."));login(email,FixtureApi.PASSWORD,"#merchant");wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("main"),"Your store is not public."));
  long company=0;String slug="";for(var store:api.get("/api/admin/stores"))if(store.get("email").asText().equals(email)){company=store.get("companyId").asLong();slug=store.get("slug").asText();}assertTrue(company>0);
  logout();login(FixtureApi.adminEmail(),FixtureApi.adminPassword(),"#marketplace-admin");
  String card="//section[contains(@class,'market-card')][.//p[contains(.,'"+email+"')]]";
  Ui.fill(driver,wait,By.xpath(card+"//input"),"Synthetic browser approval");Ui.click(driver,wait,By.xpath(card+"//button"));wait.until(ExpectedConditions.textToBePresentInElementLocated(By.xpath(card),"APPROVED"));
  logout();login(email,FixtureApi.PASSWORD,"#merchant");wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("main"),"APPROVED"));
  audit("overview-"+width);click("Products & inventory");wait.until(ExpectedConditions.visibilityOfElementLocated(Ui.id("merchant-product-form")));audit("catalog-"+width);
  for(var field:Map.of("sku",sku,"name",product,"description","Synthetic marketplace product","price","10.00").entrySet())Ui.fill(driver,wait,Ui.id("merchant-"+field.getKey()),field.getValue());Ui.fill(driver,wait,Ui.id("merchant-stock"),"4");new Select(driver.findElement(By.cssSelector("[data-testid=merchant-product-form] select"))).selectByValue("Makeup");Ui.click(driver,wait,By.cssSelector("[data-testid=merchant-product-form] button.gold-button"));wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("main"),sku));assertEquals(true,js("return document.documentElement.scrollWidth<=innerWidth"));evidence("merchant-catalog-"+width);
  click("Store announcements");wait.until(ExpectedConditions.visibilityOfElementLocated(Ui.id("merchant-offer-form")));Ui.fill(driver,wait,By.cssSelector("[data-testid=merchant-offer-form] input:not([type])"),"Synthetic announcement "+suffix);Ui.fill(driver,wait,By.cssSelector("[data-testid=merchant-offer-form] textarea"),"News for existing customers, without changing prices.");
  // Set the local date field through the native setter, then dispatch input for React.
  var end=driver.findElement(By.cssSelector("[data-testid=merchant-offer-form] input[type=datetime-local]"));((JavascriptExecutor)driver).executeScript("const setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;setter.call(arguments[0],'2099-01-01T12:00');arguments[0].dispatchEvent(new Event('input',{bubbles:true}));",end);Ui.click(driver,wait,By.cssSelector("[data-testid=merchant-offer-form] button"));wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("main"),"Saved successfully."));
  logout();String customer=api.customer();login(customer,FixtureApi.PASSWORD,"#account");open("#store/"+slug);var store=new StorePage(driver,wait);store.ready();store.onlyProduct(product);Ui.click(driver,wait,By.cssSelector(".favorite-button"));wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("main"),"Saved successfully."));
  open("#account");click("Favorites");wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("main"),product));open("#store/"+slug);store.ready();Ui.click(driver,wait,Ui.id("add-to-basket"));wait.until(ExpectedConditions.textToBePresentInElementLocated(Ui.id("basket-link"),"(1)"));open("#basket");new BasketPage(driver,wait).checkout();var checkout=new CheckoutPage(driver,wait);checkout.shipping();String number=checkout.confirm();wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("main"),"Deliveries by store"));
  open("#account");click("Offers for you");wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("main"),"Synthetic announcement "+suffix));evidence("customer-offers-"+width);
  logout();login(email,FixtureApi.PASSWORD,"#merchant");click("Store orders");Ui.click(driver,wait,Ui.id("merchant-order-row"));wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-testid=merchant-order-detail] input")));Ui.fill(driver,wait,By.cssSelector("[data-testid=merchant-order-detail] input"),"Synthetic dispatch");click("Mark as shipped");wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//button[normalize-space()='Mark as delivered']")));Ui.fill(driver,wait,By.cssSelector("[data-testid=merchant-order-detail] input"),"Synthetic delivery");click("Mark as delivered");wait.until(ExpectedConditions.textToBePresentInElementLocated(By.cssSelector("[data-testid=merchant-order-detail]"),"DELIVERED"));evidence("merchant-order-"+width);
  logout();login(customer,FixtureApi.PASSWORD,"#account");open("#order/"+number);wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-status=DELIVERED]")));assertEquals(true,js("return document.documentElement.scrollWidth<=innerWidth"));
 }
}
