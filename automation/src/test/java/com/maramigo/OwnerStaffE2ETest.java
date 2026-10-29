package com.maramigo;

import com.maramigo.support.*;
import com.maramigo.pages.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;
import org.openqa.selenium.chromium.ChromiumDriver;
import java.time.Duration;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Owner invitation, employee fulfilment and revocation use only isolated fixtures. @author Maram Abbas */
@Tag("purchase")
class OwnerStaffE2ETest extends BrowserTest {
 private void english(WebDriver d,WebDriverWait w){w.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".language-picker select")));new StorePage(d,w).language("en");}
 private void login(WebDriver d,WebDriverWait w,String email,String password,String portal){d.get(base+"/#"+portal);english(d,w);new LoginPage(d,w).submit(email,password);w.until(ExpectedConditions.urlToBe(base+(portal.equals("owner-login")?"/#owner":portal.equals("staff-login")?"/#staff-orders":"/#account")));}
 private void logout(){open("#account");wait.until(ExpectedConditions.visibilityOfElementLocated(Ui.id("logout")));Ui.click(driver,wait,Ui.id("logout"));wait.until(ExpectedConditions.urlContains("#home"));}
 private void evidence(WebDriver d,String name)throws Exception{var path=Path.of("target","screenshots");Files.createDirectories(path);Files.write(path.resolve(name+".png"),((TakesScreenshot)d).getScreenshotAs(OutputType.BYTES));}
 @ParameterizedTest @ValueSource(ints={1280,390})
 void ownerInvitesEmployeeWhoFulfilsWithoutFinancialAccess(int width)throws Exception{
  viewport(width,900);var api=new FixtureApi(base);var product=api.product(4);String customer=api.customer(),employee="employee-"+UUID.randomUUID()+"@example.test";
  login(driver,wait,FixtureApi.adminEmail(),FixtureApi.adminPassword(),"owner-login");wait.until(ExpectedConditions.visibilityOfElementLocated(Ui.id("business-profile")));
  for(var field:Map.of("ownerName","Synthetic Owner","businessName","Synthetic Store","registrationNumber","DEMO-ONLY","contactEmail","business@example.test","phone","0500000000").entrySet())Ui.fill(driver,wait,Ui.id("business-"+field.getKey()),field.getValue());
  Ui.click(driver,wait,By.cssSelector("[data-testid=business-profile] button"));wait.until(ExpectedConditions.textToBePresentInElementLocated(By.cssSelector("main [role=status]"),"Business details saved."));
  Ui.fill(driver,wait,Ui.id("invite-name"),"Synthetic Employee");Ui.fill(driver,wait,Ui.id("invite-email"),employee);Ui.click(driver,wait,By.cssSelector("[data-testid=employee-invite] button"));
  var privateLink=wait.until(ExpectedConditions.presenceOfElementLocated(Ui.id("private-invitation")));assertEquals("password",privateLink.getAttribute("type"));String invitation=privateLink.getAttribute("value");assertTrue(invitation.startsWith(base+"/#staff-register/"));
  var worker=createBrowser();var workerWait=new WebDriverWait(worker,Duration.ofSeconds(15));
  try{
   ((ChromiumDriver)worker).executeCdpCommand("Emulation.setDeviceMetricsOverride",Map.of("width",width,"height",900,"deviceScaleFactor",1,"mobile",false));
   worker.get(invitation);english(worker,workerWait);Ui.fill(worker,workerWait,Ui.id("staff-email"),employee);Ui.fill(worker,workerWait,Ui.id("staff-password"),FixtureApi.PASSWORD);Ui.click(worker,workerWait,Ui.id("staff-register-submit"));workerWait.until(ExpectedConditions.urlContains("#staff-login"));
   login(worker,workerWait,employee,FixtureApi.PASSWORD,"staff-login");workerWait.until(ExpectedConditions.visibilityOfElementLocated(Ui.id("staff-order-search")));assertTrue(worker.findElements(Ui.id("basket-link")).isEmpty());
   logout();login(driver,wait,customer,FixtureApi.PASSWORD,"login");open("#collection");var store=new StorePage(driver,wait);store.ready();store.search(product.name());store.onlyProduct(product.name());Ui.click(driver,wait,By.cssSelector("[data-testid=product-card] [data-testid=add-to-basket]"));wait.until(ExpectedConditions.textToBePresentInElementLocated(Ui.id("basket-link"),"(1)"));
   open("#basket");var basket=new BasketPage(driver,wait);basket.ready();basket.checkout();var checkout=new CheckoutPage(driver,wait);checkout.shipping();String number=checkout.confirm();
   Ui.fill(worker,workerWait,Ui.id("staff-order-search"),number);workerWait.until(ExpectedConditions.numberOfElementsToBe(By.cssSelector("[data-testid=staff-order-row]"),1));Ui.click(worker,workerWait,Ui.id("staff-order-row"));workerWait.until(ExpectedConditions.visibilityOfElementLocated(Ui.id("staff-status-form")));
   assertFalse(worker.findElement(By.tagName("main")).getText().contains("₪"));assertTrue(worker.findElements(By.cssSelector("a[href='#dashboard'],a[href='#admin'],a[href='#owner']")).isEmpty());
   Ui.fill(worker,workerWait,Ui.id("staff-status-reason"),"Synthetic dispatch");Ui.click(worker,workerWait,Ui.id("staff-status-submit"));workerWait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-testid=staff-order-detail] [data-status=SHIPPED]")));
   workerWait.until(ExpectedConditions.visibilityOfElementLocated(Ui.id("staff-status-form")));Ui.fill(worker,workerWait,Ui.id("staff-status-reason"),"Synthetic delivery");Ui.click(worker,workerWait,Ui.id("staff-status-submit"));workerWait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-testid=staff-order-detail] [data-status=DELIVERED]")));
   assertEquals(true,((JavascriptExecutor)worker).executeScript("return document.documentElement.scrollWidth<=innerWidth"));evidence(worker,"employee-workspace-"+width);
   driver.navigate().refresh();wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".order-confirmation [data-status=DELIVERED]")));
   logout();login(driver,wait,FixtureApi.adminEmail(),FixtureApi.adminPassword(),"owner-login");wait.until(ExpectedConditions.visibilityOfElementLocated(Ui.id("business-profile")));
   long employeeId=0;for(var e:api.get("/api/admin/owner/employees"))if(e.get("email").asText().equals(employee))employeeId=e.get("id").asLong();assertTrue(employeeId>0);
   By access=By.cssSelector("[data-testid=employee-access][data-employee-id='"+employeeId+"']");Ui.click(driver,wait,access);wait.until(ExpectedConditions.textToBePresentInElementLocated(access,"Restore access"));
   assertEquals(true,js("return document.documentElement.scrollWidth<=innerWidth"));evidence(driver,"owner-workspace-"+width);
   Ui.click(worker,workerWait,Ui.id("staff-refresh"));workerWait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("main a[href='#staff-login']")));assertTrue(worker.findElements(Ui.id("staff-order-detail")).isEmpty());
  }finally{worker.quit();}
 }
}
