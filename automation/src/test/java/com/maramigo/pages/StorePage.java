package com.maramigo.pages;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;
/** Catalog interactions independent of interface language. @author Maram Abbas */
public class StorePage {
 private final WebDriver driver;private final WebDriverWait wait;
 public StorePage(WebDriver driver,WebDriverWait wait){this.driver=driver;this.wait=wait;}
 public void ready(){wait.until(ExpectedConditions.numberOfElementsToBeMoreThan(By.cssSelector("[data-testid=product-card]"),0));}
 public void language(String code){new Select(driver.findElement(By.cssSelector(".language-picker select"))).selectByValue(code);wait.until(d->code.equals(d.findElement(By.tagName("html")).getAttribute("lang")));}
 public void search(String query){var input=driver.findElement(By.cssSelector("[data-testid=catalog-search]"));input.clear();input.sendKeys(query);}
 public void onlyProduct(String expected){wait.until(d->{var cards=d.findElements(By.cssSelector("[data-testid=product-card]"));return cards.size()==1&&cards.get(0).getText().contains(expected);});}
 public void firstDetails(){
  var art=wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-testid=product-card] .product-art")));
  ((JavascriptExecutor)driver).executeScript("arguments[0].scrollIntoView({block:'center',behavior:'instant'})",art);
  wait.until(d->Boolean.TRUE.equals(((JavascriptExecutor)d).executeScript("const r=arguments[0].getBoundingClientRect();const e=document.elementFromPoint(r.x+r.width/2,r.y+r.height/2);return !!e && arguments[0].contains(e)",art)));
  art.click();wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("dialog[open]")));
 }
}
