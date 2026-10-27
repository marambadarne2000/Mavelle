package com.maramigo.pages;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;
/** Stable selectors keep assertions independent of translated labels. @author Maram Abbas */
public class LoginPage {
 private final WebDriver driver;private final WebDriverWait wait;
 public LoginPage(WebDriver driver,WebDriverWait wait){this.driver=driver;this.wait=wait;}
 public void submit(String email,String password){var emailField=wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-testid=auth-email]")));emailField.clear();emailField.sendKeys(email);driver.findElement(By.cssSelector("[data-testid=auth-password]")).sendKeys(password);driver.findElement(By.cssSelector("[data-testid=auth-submit]")).click();}
 public WebElement error(){return wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-testid=form-error]")));}
}
