package com.maramigo;
import com.maramigo.support.*;
import com.maramigo.pages.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** Decline, capture, cancellation, refund and platform finance are connected through real UI/API/DB. @author Maram Abbas */
@Tag("purchase")
class PortfolioE2ETest extends BrowserTest {
 private void login(String email,String password,String destination){open("#login");new StorePage(driver,wait).language("en");new LoginPage(driver,wait).submit(email,password);wait.until(ExpectedConditions.urlToBe(base+"/"+destination));}
 private void click(String text){Ui.click(driver,wait,By.xpath("//main//button[normalize-space()='"+text+"']"));}
 private void audit(String name)throws Exception{assertEquals(true,js("return document.documentElement.scrollWidth<=innerWidth"));js(Files.readString(Path.of("..","frontend","node_modules","axe-core","axe.min.js")));driver.manage().timeouts().scriptTimeout(java.time.Duration.ofSeconds(45));String result=(String)((JavascriptExecutor)driver).executeAsyncScript("const done=arguments[arguments.length-1];axe.run(document,{runOnly:{type:'tag',values:['wcag2a','wcag2aa','wcag21aa','wcag22aa','best-practice']},resultTypes:['violations','incomplete']}).then(r=>done(JSON.stringify({violations:r.violations,incomplete:r.incomplete}))).catch(e=>done(JSON.stringify({error:e.message})))");var report=new com.fasterxml.jackson.databind.ObjectMapper().readTree(result);var folder=Path.of("target","accessibility");Files.createDirectories(folder);Files.writeString(folder.resolve("portfolio-"+name+".json"),result);assertFalse(report.has("error"));assertEquals(0,report.get("violations").size(),report.get("violations").toString());var screenshots=Path.of("target","screenshots");Files.createDirectories(screenshots);Files.write(screenshots.resolve("portfolio-"+name+".png"),((TakesScreenshot)driver).getScreenshotAs(OutputType.BYTES));}
 @ParameterizedTest @ValueSource(ints={1280,390})
 void declinedPaymentThenPurchaseAndCancellationReconcileFinance(int width)throws Exception{
  viewport(width,900);var api=new FixtureApi(base);var product=api.product(3);String customer=api.customer();login(customer,FixtureApi.PASSWORD,"#account");open("#collection");var store=new StorePage(driver,wait);store.ready();store.search(product.name());store.onlyProduct(product.name());Ui.click(driver,wait,Ui.id("add-to-basket"));wait.until(ExpectedConditions.textToBePresentInElementLocated(Ui.id("basket-link"),"(1)"));open("#basket");new BasketPage(driver,wait).checkout();var checkout=new CheckoutPage(driver,wait);checkout.shipping();new Select(driver.findElement(By.cssSelector("[data-testid=checkout-form] select"))).selectByValue("DECLINED");Ui.click(driver,wait,Ui.id("checkout-submit"));wait.until(ExpectedConditions.textToBePresentInElementLocated(By.cssSelector("[role=alert]"),"Simulated payment declined"));assertEquals(3,api.stock(product));new Select(driver.findElement(By.cssSelector("[data-testid=checkout-form] select"))).selectByValue("SUCCESS");String number=checkout.confirm();
  var aftercare=Ui.id("order-aftercare");wait.until(ExpectedConditions.textToBePresentInElementLocated(aftercare,"CAPTURED"));new Select(driver.findElement(By.cssSelector("[data-testid=order-aftercare] select"))).selectByValue("1");Ui.fill(driver,wait,By.cssSelector("[data-testid=order-aftercare] textarea"),"Synthetic cancellation from the full UI");click("Cancel this store part");wait.until(ExpectedConditions.textToBePresentInElementLocated(aftercare,"REFUNDED"));assertEquals(3,api.stock(product));audit("aftercare-"+width);
  open("#account");Ui.click(driver,wait,Ui.id("logout"));wait.until(ExpectedConditions.urlContains("#home"));login(FixtureApi.adminEmail(),FixtureApi.adminPassword(),"#marketplace-admin");click("Platform finance");wait.until(ExpectedConditions.textToBePresentInElementLocated(Ui.id("finance-panel"),number));audit("finance-"+width);
 }
}
