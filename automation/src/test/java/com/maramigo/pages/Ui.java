package com.maramigo.pages;
import org.openqa.selenium.*;import org.openqa.selenium.support.ui.*;
/** Real clicks and explicit condition waits shared by Page Objects. @author Maram Abbas */
public final class Ui {
 public static By id(String value){return By.cssSelector("[data-testid='"+value+"']");}
 public static void click(WebDriver driver,WebDriverWait wait,By by){
  var element=wait.until(ExpectedConditions.elementToBeClickable(by));((JavascriptExecutor)driver).executeScript("arguments[0].scrollIntoView({block:'center',behavior:'instant'})",element);
  wait.until(d->Boolean.TRUE.equals(((JavascriptExecutor)d).executeScript("const r=arguments[0].getBoundingClientRect(),e=document.elementFromPoint(r.x+r.width/2,r.y+r.height/2);return !!e&&arguments[0].contains(e)",element)));element.click();
 }
 public static void fill(WebDriver driver,WebDriverWait wait,By by,String value){var field=wait.until(ExpectedConditions.visibilityOfElementLocated(by));field.sendKeys(Keys.chord(Keys.CONTROL,"a"),Keys.BACK_SPACE,value);}
}
