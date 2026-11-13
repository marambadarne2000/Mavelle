package com.maramigo.marketplace;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.*;
/** Commercial proposal only: cannot activate billing or imply verified tax status. @author Maram Abbas */
@RestController
public class BusinessPlanController {
 public record Plan(String version,String status,String currency,BigDecimal monthlyFee,BigDecimal commissionPercent,
  String priceBasis,String commissionBasis,String taxStatus,boolean chargesEnabled,boolean paymentsConnected,String providerCandidate){}
 @GetMapping("/api/business-plan") public Plan plan(){return new Plan("launch-draft-2026-10-30","DRAFT","ILS",new BigDecimal("99.00"),new BigDecimal("5.00"),"INCLUSIVE_OF_APPLICABLE_VAT","SETTLED_PRODUCT_VALUE_AFTER_DISCOUNTS_AND_REFUNDS_EXCLUDING_SHIPPING","UNVERIFIED",false,false,"PayMe (candidate; not connected)");}
}
