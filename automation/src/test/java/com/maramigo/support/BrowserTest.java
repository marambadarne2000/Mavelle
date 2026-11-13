package com.maramigo.support;

import java.net.URI;
import java.nio.file.*;
import java.time.Duration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.openqa.selenium.edge.*;
import org.openqa.selenium.chromium.ChromiumDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

/** Fresh independent browser, explicit waits and failure evidence. @author Maram Abbas */
public abstract class BrowserTest {
 protected WebDriver driver;protected WebDriverWait wait;
 protected final String base=System.getProperty("baseUrl","http://127.0.0.1:5173");
 @RegisterExtension final Evidence evidence=new Evidence();
 final class Evidence implements AfterTestExecutionCallback,LifecycleMethodExecutionExceptionHandler{
  private void capture(ExtensionContext context){if(driver instanceof TakesScreenshot screenshot){try{Path dir=Path.of("target","screenshots");Files.createDirectories(dir);Files.write(dir.resolve(context.getRequiredTestMethod().getName()+"-"+System.nanoTime()+".png"),screenshot.getScreenshotAs(OutputType.BYTES));}catch(Exception ignored){}}}
  @Override public void afterTestExecution(ExtensionContext context){if(context.getExecutionException().isPresent())capture(context);}
  @Override public void handleBeforeEachMethodExecutionException(ExtensionContext context,Throwable error)throws Throwable{capture(context);throw error;}
 }
 @BeforeEach void start(){
  if(!java.util.Set.of("127.0.0.1","localhost","::1").contains(URI.create(base).getHost()))throw new IllegalArgumentException("This suite is restricted to a local demo.");
  driver=createBrowser();wait=new WebDriverWait(driver,Duration.ofSeconds(15));
 }
 protected WebDriver createBrowser(){
  WebDriver browser;
  boolean headless=Boolean.parseBoolean(System.getProperty("headless","true"));
  if(System.getProperty("browser","chrome").equals("edge")){var options=new EdgeOptions();if(headless)options.addArguments("--headless=new");options.addArguments("--window-size=1280,900","--force-prefers-reduced-motion","--no-first-run","--no-default-browser-check");browser=new EdgeDriver(options);}
  else{var options=new ChromeOptions();if(headless)options.addArguments("--headless=new");options.addArguments("--window-size=1280,900","--disable-search-engine-choice-screen","--force-prefers-reduced-motion","--no-first-run","--no-default-browser-check");options.setExperimentalOption("prefs",java.util.Map.of("credentials_enable_service",false,"profile.password_manager_enabled",false));browser=new ChromeDriver(options);}
  try{browser.manage().timeouts().implicitlyWait(Duration.ZERO);browser.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(25));return browser;}
  catch(RuntimeException error){try{browser.quit();}catch(WebDriverException cleanup){error.addSuppressed(cleanup);}throw error;}
 }
 @AfterEach void stop(){if(driver!=null)driver.quit();}
 protected void open(String route){driver.get(base+"/"+route);}
 protected Object js(String source,Object...args){return ((JavascriptExecutor)driver).executeScript(source,args);}
 protected void viewport(int width,int height){driver.manage().window().setSize(new Dimension(width,height));((ChromiumDriver)driver).executeCdpCommand("Emulation.setDeviceMetricsOverride",java.util.Map.of("width",width,"height",height,"deviceScaleFactor",1,"mobile",false));}
}
