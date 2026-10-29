# Maramigo Selenium automation

Java 17 · Selenium 4.51.0 · JUnit 5.12.2 · Page Objects · Chrome/Edge · headless by default.

Start the application, then run from the project root:

```powershell
./scripts/Test-Automation.ps1
# Full purchase suite in a disposable schema and separate application:
./scripts/Test-Automation.ps1 -Full
# Optional; Edge and visible mode have not been verified here:
./scripts/Test-Automation.ps1 -Browser edge
./scripts/Test-Automation.ps1 -Visible
```

Direct Maven usage from this folder: `mvn test -DbaseUrl=http://127.0.0.1:5173 -Dbrowser=chrome -Dheadless=true`. The suite rejects non-loopback destinations. The wrapper puts the driver cache in ignored `.tools/selenium`, disables Selenium Manager statistics and uses portable Maven when present. Initial dependencies/driver downloads need internet; see [official Selenium Manager documentation](https://www.selenium.dev/documentation/selenium_manager/).

Six Chrome cases passed with no skipped tests:

| Journey | Scope |
|---|---|
| Search, product details, keyboard wrap and Escape | English 1280×900; Hebrew and Arabic 390×844 |
| Store catalog and unknown-page recovery | Noir Atelier isolation and route back home |
| Invalid login | Unique synthetic nonexistent email, focused error, enabled retry |
| Skip navigation | First Tab reaches skip; Enter focuses content without changing route |

Each smoke case uses a fresh browser profile and changes no products, orders or stock. Public checks assume seeded demo names and stores still exist. The full mode runs those six cases plus six isolated purchase cases and six accessibility matrix cases and two owner/employee cases (20 total). The purchase cases cover:

| Purchase journey | Assertions |
|---|---|
| Signup → anonymous add → login → basket → checkout → logout | 1280px and 390px; empty basket, exactly one resumed add, quantity/reload persistence, required shipping, server order number, totals, stock and protected order history |
| Last-unit purchase with a stale second browser | First customer succeeds; second cannot add the now sold-out unit |
| Price change before checkout | Refresh warns and uses the new price; a later product price change does not change the order snapshot |
| Sold-out and deactivated product | Checkout is blocked; an unavailable item remains removable |
| Customer/admin browsers | Manager opens the list before purchase, refreshes to see the order, cancels it, and customer sees cancellation; original stock is restored |

Full mode requires the prepared local PostgreSQL runtime, npm dependencies and a current backend JAR. Rebuild a stale JAR with `Start-Local.ps1 -Restart -Build`. The launcher creates an `e2e_<random UUID>` PostgreSQL schema, selects free loopback ports and starts a separate backend/frontend with a dedicated Vite cache. JDBC currentSchema, Hibernate schema and Flyway schema all point to that namespace. A schema-specific administrator bootstrap check completes before tests begin. Its higher login limit applies only to that disposable backend.

`FixtureApi` uses normal authenticated/CSRF-protected APIs to create uniquely named products and synthetic customers. Test shipping is synthetic. No reset endpoint exists. Full tests require an isolated-launcher marker and generated administrator credentials; the normal demo administrator is not used. Finally, the launcher stops only its own child processes, drops only its validated generated schema, and restores environment variables. Logs/cache remain in ignored `.runtime/e2e_*` folders for diagnosis. Abrupt host termination can interrupt normal cleanup; no cleanup guarantee is claimed after power loss or forced process termination.

Page Objects encapsulate selectors. The base class uses explicit waits, never fixed sleeps, and always quits WebDriver. Clicks are native WebDriver clicks. Reduced-motion preference is supplied as a Chromium startup switch and asserted with matchMedia, avoiding an early renderer-dependent CDP media command. Chromium device metrics set exact viewport widths, which tests assert along with absence of horizontal overflow. Dynamic text uses locator-based expected conditions or a narrowly scoped refreshed condition so React rerenders do not leave stale element handles. No whole-test retry hides a failed scenario.

Smoke reports: `target/surefire-reports/`; full reports: `target/full-reports/`. Failure screenshots and selected successful evidence: `target/screenshots/`. These are ignored by Git. Failure capture runs before teardown and covers fixture setup failures too; custom diagnostics do not save passwords, cookies or page source.

An opt-in diagnostic deliberately fails to verify screenshot capture; it is outside the normal `*Test` suite:

```powershell
mvn -Dtest=DiagnosticsProbe -DreportDirectory=target/diagnostic-probe test
```

Only this command is expected to return nonzero and create `deliberateFailureProducesScreenshot-*.png`. It was verified, followed by a passing normal suite. Its report is isolated from normal reports.

Verified: Chrome 156 with matching ChromeDriver 156. Selenium emits a warning that its nearest typed CDP module is 155; these WebDriver tests and raw viewport commands passed. Edge remains unverified. Hosted checks and artifacts are recorded on the private repository's Actions page; inspect the current head rather than assuming a previous run covers new code.

Full purchase flow uses language-independent selectors. The accessibility matrix also completes checkout and visits account, order and administrator views in English, Hebrew and Arabic at 1280px and 390px. It adds six cases with 21 sampled views each. Run only that matrix using `Test-Automation.ps1 -Full -AccessibilityOnly`; its JUnit reports use `target/accessibility-reports/`, while per-view axe results use `target/accessibility/`. See [matrix coverage and limits](../docs/ACCESSIBILITY_MATRIX.md). Targeted mode does not certify a new run of the other 12 cases. No E2E coverage percentage or full WCAG certification is claimed.

OwnerStaffE2ETest adds desktop/mobile business-profile save, invitation activation, employee fulfilment without financial fields, customer tracking and employee revocation. All fixtures remain isolated. Public smoke also verifies owner and employee entry links on the normal sign-in page.
