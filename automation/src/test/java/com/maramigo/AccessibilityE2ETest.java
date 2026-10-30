package com.maramigo;

import com.fasterxml.jackson.databind.*;
import com.maramigo.pages.*;
import com.maramigo.support.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** WCAG rule samples and responsive journeys use disposable synthetic data only. @author Maram Abbas */
@Tag("purchase")
class AccessibilityE2ETest extends BrowserTest {
 private final ObjectMapper json=new ObjectMapper();
 private final List<JsonNode> scans=new ArrayList<>();
 private final List<String> problems=new ArrayList<>();
 private String language;private int width;
 private void route(String route,String ready){open(route);wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(ready)));}
 private void language(){new StorePage(driver,wait).language(language);}
 private void login(String email,String password){boolean owner=email.equals(FixtureApi.adminEmail());route(owner?"#owner-login":"#login","[data-testid=auth-email]");language();new LoginPage(driver,wait).submit(email,password);wait.until(ExpectedConditions.urlToBe(base+(owner?"/#marketplace-admin":"/#account")));if(owner){route("#owner","[data-testid=business-profile]");auditOwner();}route("#account",".account-home");}
 private void auditOwner(){try{audit("owner-workspace");}catch(Exception e){throw new IllegalStateException(e);}}
 private void audit(String view)throws Exception{
  wait.until(d->Boolean.TRUE.equals(js("return document.fonts.status==='loaded'")));
  if(!Boolean.TRUE.equals(js("return document.documentElement.scrollWidth<=innerWidth")))problems.add(view+": horizontal overflow");
  assertEquals(language,driver.findElement(By.tagName("html")).getAttribute("lang"));
  assertEquals(language.equals("en")?"ltr":"rtl",driver.findElement(By.tagName("html")).getAttribute("dir"));
  js(Files.readString(Path.of("..","frontend","node_modules","axe-core","axe.min.js")));
  driver.manage().timeouts().scriptTimeout(java.time.Duration.ofSeconds(45));
  String result=(String)((JavascriptExecutor)driver).executeAsyncScript("""
   const done=arguments[arguments.length-1];
   axe.run(document,{runOnly:{type:'tag',values:['wcag2a','wcag2aa','wcag21a','wcag21aa','wcag22aa','best-practice']},resultTypes:['violations','incomplete']})
    .then(r=>done(JSON.stringify({version:r.testEngine.version,violations:r.violations.map(v=>({id:v.id,impact:v.impact,help:v.help,nodes:v.nodes.map(n=>({target:n.target,summary:n.failureSummary}))})),incomplete:r.incomplete.map(v=>({id:v.id,targets:v.nodes.map(n=>n.target)}))})))
    .catch(e=>done(JSON.stringify({error:e.message})));
   """);
  var data=json.readTree(result);assertFalse(data.has("error"),"Accessibility scan execution failed");
  ((com.fasterxml.jackson.databind.node.ObjectNode)data).put("view",view);scans.add(data);
  for(var violation:data.get("violations"))problems.add(view+": "+violation.get("id").asText()+" "+violation.get("nodes"));
 }
 private void screenshot(String view)throws Exception{var folder=Path.of("target","screenshots");Files.createDirectories(folder);Files.write(folder.resolve("review-"+view+"-"+language+"-"+width+".png"),((TakesScreenshot)driver).getScreenshotAs(OutputType.BYTES));}
 @AfterEach void report()throws Exception{
  if(language==null)return;var folder=Path.of("target","accessibility");Files.createDirectories(folder);
  json.writerWithDefaultPrettyPrinter().writeValue(folder.resolve(language+"-"+width+".json").toFile(),Map.of("checkedAt",java.time.Instant.now().toString(),"language",language,"width",width,"scans",scans,"problems",problems));
 }
 @ParameterizedTest @CsvSource({"en,1280","en,390","he,1280","he,390","ar,1280","ar,390"})
 void publicCustomerAndAdministratorViews(String language,int width)throws Exception{
  this.language=language;this.width=width;viewport(width,900);var api=new FixtureApi(base);var product=api.product(5);var customer=api.customer();
  route("#home","[data-testid=product-card]");language();audit("home");
  new StorePage(driver,wait).firstDetails();audit("product-dialog");driver.findElement(By.cssSelector("dialog .close")).sendKeys(Keys.ESCAPE);wait.until(ExpectedConditions.numberOfElementsToBe(By.cssSelector("dialog[open]"),0));
  route("#store/aurea-living","[data-testid=product-card]");audit("store");
  route("#login","[data-testid=auth-email]");audit("login");
  route("#register","[data-testid=auth-email]");audit("register");
  route("#join-business","[data-testid=business-registration]");audit("business-registration");
  route("#owner-login","[data-testid=auth-email]");audit("owner-login");route("#staff-login","[data-testid=auth-email]");audit("employee-login");route("#staff-register","[data-testid=staff-registration]");audit("employee-registration");
  route("#basket",".basket-empty");audit("empty-basket");
  route("#missing-page","main h1");audit("missing-page");
  login(customer,FixtureApi.PASSWORD);audit("customer-account");
  route("#collection","[data-testid=product-card]");var store=new StorePage(driver,wait);store.search(product.name());store.onlyProduct(product.name());Ui.click(driver,wait,By.cssSelector("[data-testid=product-card] [data-testid=add-to-basket]"));
  wait.until(ExpectedConditions.textToBePresentInElementLocated(Ui.id("basket-link"),"(1)"));route("#basket",".basket-item");audit("basket");
  new BasketPage(driver,wait).checkout();wait.until(ExpectedConditions.visibilityOfElementLocated(Ui.id("checkout-form")));audit("checkout");screenshot("checkout");
  Ui.click(driver,wait,Ui.id("shipping-fullName"));
  for(String next:List.of("shipping-phone","shipping-address","shipping-city","shipping-postalCode","shipping-country","checkout-submit")){
   driver.switchTo().activeElement().sendKeys(Keys.TAB);assertEquals(next,driver.switchTo().activeElement().getAttribute("data-testid"),"Checkout keyboard order");
  }
  var checkout=new CheckoutPage(driver,wait);checkout.shipping();String number=checkout.confirm();audit("order-confirmation");
  route("#orders",".order-list");audit("customer-orders");
  route("#account","[data-testid=logout]");Ui.click(driver,wait,Ui.id("logout"));wait.until(ExpectedConditions.urlContains("#home"));
  login(FixtureApi.adminEmail(),FixtureApi.adminPassword());audit("administrator-account");
  route("#marketplace-admin","main h1");audit("marketplace-admin");
  route("#dashboard","[data-testid=dashboard-low-stock]");audit("dashboard");screenshot("dashboard");
  route("#admin","[data-testid=admin-product-row]");Ui.fill(driver,wait,By.cssSelector(".admin-card input[type=search]"),product.name());
  wait.until(ExpectedConditions.numberOfElementsToBe(By.cssSelector("[data-testid=admin-product-row]"),1));Ui.click(driver,wait,Ui.id("admin-product-row"));wait.until(ExpectedConditions.visibilityOfElementLocated(Ui.id("stock-adjustment-form")));audit("product-and-stock-editor");
  route("#admin-orders","[data-testid=admin-order-search]");Ui.fill(driver,wait,Ui.id("admin-order-search"),number);wait.until(ExpectedConditions.numberOfElementsToBe(By.cssSelector("[data-testid=admin-order-row]"),1));
  Ui.click(driver,wait,Ui.id("admin-order-row"));wait.until(ExpectedConditions.visibilityOfElementLocated(Ui.id("order-status-form")));audit("order-management");
  new Select(driver.findElement(By.cssSelector("[data-testid=order-status-form] select"))).selectByValue("CANCELLED");wait.until(ExpectedConditions.visibilityOfElementLocated(Ui.id("cancel-confirm")));audit("cancellation-confirmation");
  assertTrue(problems.isEmpty(),String.join("\n",problems));
 }
}
