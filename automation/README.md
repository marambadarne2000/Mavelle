# Mavelle Selenium automation

Java 17 · Selenium 4.51.0 · JUnit 5.12.2 · Page Objects · Chrome/Edge · headless by default.

Start the application with `scripts/Start-Local.ps1`, which enables `demo,real-catalog`, then run from the project root:

```powershell
# Thirteen read-only storefront/manufacturer/basket cases against the local application:
./scripts/Test-Automation.ps1
# Thirty-five cases in a disposable schema and separate application:
./scripts/Test-Automation.ps1 -Full
# Six accessibility matrix cases only:
./scripts/Test-Automation.ps1 -Full -AccessibilityOnly
# Three support/notification journeys only:
./scripts/Test-Automation.ps1 -Full -SupportOnly
# Optional; Edge and visible mode remain unverified:
./scripts/Test-Automation.ps1 -Browser edge
./scripts/Test-Automation.ps1 -Visible
```

Direct Maven: `mvn test -DbaseUrl=http://127.0.0.1:5173 -Dbrowser=chrome -Dheadless=true` from this folder. The default suite rejects non-loopback destinations and expects both demo profiles' seed data. The wrapper uses the ignored `.tools/selenium` driver cache, disables Selenium Manager statistics and uses portable Maven when present. Initial driver/dependency downloads need internet; see [official Selenium Manager documentation](https://www.selenium.dev/documentation/selenium_manager/).

| Suite | Cases | Main assertions |
|---|---:|---|
| StorefrontTest | 7 | English/Hebrew/Arabic search and product dialog; focus wrap/Escape; store isolation; unknown-route recovery; login error; skip navigation; mobile back/discovery/reduced motion |
| ManufacturerCatalogTest | 3 | Five real brand catalogs; twenty matching loaded photos; per-store filtering; source links/specifications; desktop and Hebrew/Arabic mobile; guest add redirects to login |
| PurchaseE2ETest | 7 | Full checkout at two widths; last unit; price review; a fresh repeated purchase; unavailable products; administrative cancellation and stock restoration |
| OwnerStaffE2ETest | 2 | Profile, invitation, employee activation, fulfilment without financial access, revocation and customer tracking |
| MarketplaceE2ETest | 2 | Business application, approval, merchant products/announcements, favorites, purchase and fulfilment |
| PortfolioE2ETest | 2 | Connected simulated payment, returns/refunds, private account workflows and portfolio views |
| AccessibilityE2ETest | 6 | English/Hebrew/Arabic at 1280px and 390px; keyboard, focus and axe samples through shopping/account/order/admin states |
| SupportE2ETest | 3 | Customer ticket, owner review/reply in a second browser, private notifications, read receipts and resolution in English/Hebrew/Arabic |
| CartImagesTest | 3 | Distinct loaded basket photos, server lookup matching, contain layout and an honest missing-photo state at desktop/Hebrew/Arabic mobile |
| **Full suite** | **35** | **Thirteen read-only cases plus twenty-two isolated workflow/matrix cases** |

The full launcher uses a fresh PostgreSQL schema, randomized owner credentials, separate ports and an isolated account-encryption key. It never uses normal customer orders. The launcher removes the generated schema and stops its own processes in `finally`; abrupt host termination can interrupt cleanup. Diagnostic logs remain in ignored `.runtime/e2e_*` directories.

Browser interactions use explicit waits and native WebDriver clicks, with no whole-test retries. Each test starts a fresh browser and quits it in teardown. Reduced motion is set through Chromium startup options; exact viewport dimensions and absence of horizontal overflow are asserted. Failure screenshots are captured before teardown, including setup failures, without saving passwords, cookies or page source.

Reports: `target/surefire-reports/` for public smoke, `target/full-reports/` for full mode, `target/accessibility-reports/` for targeted accessibility, `target/support-reports/` for targeted support. Screenshots and axe results are in ignored `target/screenshots/` and `target/accessibility/`. The six matrix cases sample 31 states each, with additional merchant/portfolio samples; see [coverage and limitations](../docs/ACCESSIBILITY_MATRIX.md). Targeted mode certifies only the selected cases. No E2E coverage percentage or full WCAG certification is claimed.

Verified browser: Chrome 156 with matching ChromeDriver 156. Selenium warns that its nearest typed CDP module is 155; the raw viewport commands and WebDriver journeys are checked by the suite. Edge remains unverified. Hosted results on the private repository's Actions page must match the current commit.

An opt-in screenshot-capture diagnostic, outside the ordinary `*Test` selection:

```powershell
mvn -Dtest=DiagnosticsProbe -DreportDirectory=target/diagnostic-probe test
```
