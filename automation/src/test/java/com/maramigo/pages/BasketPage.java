package com.maramigo.pages;
import org.openqa.selenium.*;import org.openqa.selenium.support.ui.*;
/** Basket quantities and live server prices. @author Maram Abbas */
public class BasketPage {
 private final WebDriver driver;private final WebDriverWait wait;
 public BasketPage(WebDriver driver,WebDriverWait wait){this.driver=driver;this.wait=wait;}
 public void ready(){wait.until(ExpectedConditions.visibilityOfElementLocated(Ui.id("basket-item")));}
 public void quantity(long productId,int value){Ui.fill(driver,wait,By.id("quantity-"+productId),""+value);Ui.click(driver,wait,By.cssSelector(".basket-update"));wait.until(d->String.valueOf(value).equals(d.findElement(By.id("quantity-"+productId)).getAttribute("value")));}
 public void subtotal(String amount){wait.until(d->d.findElement(Ui.id("basket-subtotal")).getText().contains(amount));}
 public void checkout(){Ui.click(driver,wait,By.cssSelector("a[href='#checkout']"));wait.until(ExpectedConditions.visibilityOfElementLocated(Ui.id("checkout-form")));}
 public void refresh(){Ui.click(driver,wait,Ui.id("cart-refresh"));}
}
