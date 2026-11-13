package com.maramigo.pages;
import org.openqa.selenium.*;import org.openqa.selenium.support.ui.*;import java.util.Map;
/** Browser checkout; fixtures supply synthetic shipping only. @author Maram Abbas */
public class CheckoutPage {
 private final WebDriver driver;private final WebDriverWait wait;
 public CheckoutPage(WebDriver driver,WebDriverWait wait){this.driver=driver;this.wait=wait;}
 public void shipping(){for(var field:Map.of("fullName","Selenium Customer","phone","0500000000","address","1 Synthetic Street","city","Demo City","postalCode","0000000","country","IL").entrySet())Ui.fill(driver,wait,Ui.id("shipping-"+field.getKey()),field.getValue());}
 public String confirm(){Ui.click(driver,wait,Ui.id("checkout-submit"));wait.until(ExpectedConditions.urlContains("#order/MG-"));return wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".order-confirmation .order-number"))).getText();}
 public void requiredFieldsPreventSubmit(){Ui.click(driver,wait,Ui.id("checkout-submit"));if(!Boolean.FALSE.equals(((JavascriptExecutor)driver).executeScript("return document.querySelector('[data-testid=checkout-form]').checkValidity()")))throw new AssertionError("Empty shipping form should be invalid");if(!driver.getCurrentUrl().endsWith("#checkout"))throw new AssertionError("Invalid checkout navigated away");}
}
