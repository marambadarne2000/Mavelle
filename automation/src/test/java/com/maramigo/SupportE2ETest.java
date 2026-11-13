package com.maramigo;
import com.maramigo.support.*;
import com.maramigo.pages.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** A private support request travels from customer to owner and back. @author Maram Abbas */
@Tag("purchase")
class SupportE2ETest extends BrowserTest {
 private void login(String email,String password,String language){open("#login");new StorePage(driver,wait).language(language);new LoginPage(driver,wait).submit(email,password);wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("#login")));}
 private void audit(String name)throws Exception{
  assertEquals(true,js("return document.documentElement.scrollWidth<=innerWidth"));
  js(Files.readString(Path.of("..","frontend","node_modules","axe-core","axe.min.js")));
  driver.manage().timeouts().scriptTimeout(java.time.Duration.ofSeconds(45));
  String result=(String)((JavascriptExecutor)driver).executeAsyncScript("const done=arguments[arguments.length-1];axe.run(document,{runOnly:{type:'tag',values:['wcag2a','wcag2aa','wcag21aa','wcag22aa','best-practice']},resultTypes:['violations','incomplete']}).then(r=>done(JSON.stringify({violations:r.violations,incomplete:r.incomplete}))).catch(e=>done(JSON.stringify({error:e.message})))");
  var report=new com.fasterxml.jackson.databind.ObjectMapper().readTree(result);var folder=Path.of("target","accessibility");Files.createDirectories(folder);Files.writeString(folder.resolve("support-"+name+".json"),result);assertFalse(report.has("error"));assertEquals(0,report.get("violations").size(),report.get("violations").toString());
  var screenshots=Path.of("target","screenshots");Files.createDirectories(screenshots);Files.write(screenshots.resolve("support-"+name+".png"),((TakesScreenshot)driver).getScreenshotAs(OutputType.BYTES));
 }
 @ParameterizedTest @CsvSource({"1280,900,en","390,844,he","390,844,ar"})
 void customerRequestOwnerReplyNotificationAndResolution(int width,int height,String language)throws Exception{
  viewport(width,height);var api=new FixtureApi(base);String email=api.customer();login(email,FixtureApi.PASSWORD,language);open("#support");
  wait.until(ExpectedConditions.visibilityOfElementLocated(Ui.id("support-create")));Ui.fill(driver,wait,By.cssSelector("[data-testid=support-create] input"),"Synthetic delivery question");Ui.fill(driver,wait,By.cssSelector("[data-testid=support-create] textarea"),"Please help with this isolated demonstration.");
  Ui.click(driver,wait,By.cssSelector("[data-testid=support-create] button"));wait.until(ExpectedConditions.urlMatches(".*#support/[1-9][0-9]*$"));String route=driver.getCurrentUrl().substring(driver.getCurrentUrl().indexOf('#'));long id=Long.parseLong(route.substring(9));
  wait.until(ExpectedConditions.textToBePresentInElementLocated(Ui.id("support-detail"),"Synthetic delivery question"));audit(language+"-request-"+width);
  var manager=createBrowser();var managerWait=new WebDriverWait(manager,java.time.Duration.ofSeconds(15));
  try{manager.get(base+"/#login");new StorePage(manager,managerWait).language(language);new LoginPage(manager,managerWait).submit(FixtureApi.adminEmail(),FixtureApi.adminPassword());managerWait.until(ExpectedConditions.urlContains("#marketplace-admin"));manager.get(base+"/"+route);managerWait.until(ExpectedConditions.visibilityOfElementLocated(Ui.id("support-detail")));Ui.click(manager,managerWait,By.cssSelector("[data-testid=support-detail] .engagement-tools button:nth-of-type(2)"));managerWait.until(d->d.findElements(By.cssSelector("[data-testid=support-detail] .engagement-tools button")).size()==2);Ui.fill(manager,managerWait,By.cssSelector("[data-testid=support-reply] textarea"),"The platform has reviewed your request.");Ui.click(manager,managerWait,By.cssSelector("[data-testid=support-reply] button"));managerWait.until(ExpectedConditions.textToBePresentInElementLocated(By.cssSelector(".support-thread"),"The platform has reviewed your request."));}finally{manager.quit();}
  open("#updates");wait.until(ExpectedConditions.numberOfElementsToBeMoreThan(Ui.id("update-item"),0));
  assertFalse(driver.findElements(By.cssSelector("a[href='"+route+"']")).isEmpty());audit(language+"-updates-"+width);
  Ui.click(driver,wait,By.cssSelector(".engagement-tools .gold-button"));wait.until(d->d.findElements(By.cssSelector(".notice.unread")).isEmpty());
  open(route);wait.until(ExpectedConditions.textToBePresentInElementLocated(Ui.id("support-detail"),"The platform has reviewed your request."));
  Ui.fill(driver,wait,By.cssSelector("[data-testid=support-reply] textarea"),"Thank you, this is resolved.");Ui.click(driver,wait,By.cssSelector("[data-testid=support-reply] button"));wait.until(ExpectedConditions.textToBePresentInElementLocated(By.cssSelector(".support-thread"),"Thank you, this is resolved."));
  Ui.click(driver,wait,By.cssSelector("[data-testid=support-detail] .engagement-tools button:last-child"));wait.until(ExpectedConditions.numberOfElementsToBe(Ui.id("support-reply"),0));audit(language+"-resolved-"+width);
 }
}
