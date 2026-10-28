# Acceptance matrix

All statuses below describe implementation, not successful execution. Execution evidence is in STATUS_HE.md.

| ID | Requirement | Evidence in source | State |
|---|---|---|---|
| CAT-01 | Only active products returned | repository predicate + PostgreSQL test | Passed |
| CAT-02 | Search ignores case and trims query | MVC + PostgreSQL + browser | Passed |
| CAT-03 | Bounded pagination, stable ordering | MVC + PostgreSQL test | Passed |
| CAT-04 | Unknown or inactive detail is 404 | MVC + PostgreSQL test | Passed |
| CAT-05 | Public catalog has no write operation | MVC test | POST rejection test passed |
| CAT-06 | Category filters and price ordering | PostgreSQL integration test | Passed, including combined category/search and invalid options |
| ADM-01 | ADMIN-only product reads/writes and CSRF | AdminCatalogIT | Passed |
| ADM-02 | Create/edit/deactivate, duplicate SKU and strict input | AdminCatalogIT + browser | Passed |
| ADM-03 | Metadata edits preserve inventory | AdminCatalogIT + browser | Passed |
| INV-01 | Audited stock adjustments and negative-stock rejection | AdminCatalogIT + browser | Passed |
| INV-02 | Concurrent changes using the same version have one winner | AdminCatalogIT | Passed: HTTP 200 and 409, zero remaining stock, one successful adjustment |
| UI-01 | Visible loading, error and empty states | React catalog state | Loading/empty observed in browser; forced outage behavior not manually checked |
| UI-02 | Responsive catalog and accessible dialog | CSS breakpoints + native dialog | Desktop and dialog observed; mobile audit pending |
| DB-01 | Empty PostgreSQL migrates and returns demo catalog | CatalogIT | Passed using portable PostgreSQL 17.11 |
| AUTH-01 | Sessions, CSRF and role checks | AuthIT, limiter tests, customer UI flow, local admin smoke | Passed |
| BASKET-01 | Add, merge, edit quantity, remove and clear | Model tests + browser | Passed |
| BASKET-02 | Persistence and same-origin tab updates | Model tests + browser reload/two tabs | Passed |
| BASKET-03 | Current server prices and availability | PostgreSQL lookup + fixture changes in browser | Passed: price update, insufficient stock, zero stock and deactivation |
| BASKET-04 | Corrupt saved state and monetary boundaries | 14 model tests | Passed; forced browser storage denial not injected |
| BASKET-05 | Basket survives customer sign-in | Browser customer flow | Passed: three units, unchanged ILS 507 subtotal |
| ORD-01 | Atomic purchase and nonnegative stock | Architecture contract | Planned |
| ORD-02 | Idempotent checkout | Architecture contract | Planned |
| ORD-03 | One winner for last item under contention | Integration scenario | Planned |
| QA-01 | Customer/admin browser workflow | Selenium plan | Planned |

Before implementing state-changing endpoints, add authentication, CSRF protection and ownership authorization tests. The current service is deliberately read-only.

## Added in the stores / checkout stage

| ID | Requirement | Evidence | State |
|---|---|---|---|
| LOC-01 | Hebrew, Arabic, English and RTL/LTR | Translation checks + browser | Passed |
| STORE-01 | Separate fictional company catalogs | CatalogIT + Arabic browser catalog | Passed |
| AUTH-ADD | Login before add; resume exactly once | Browser 0 → login → 1 | Passed |
| ORD-01 | Atomic checkout and price snapshots | OrderIT + browser | Passed |
| ORD-02 | Last-unit concurrency and idempotency | PostgreSQL OrderIT | Passed |
| ORD-03 | Owned history, detail and printable demo confirmation | OrderIT + browser | Passed |

| ADM-ORD-01 | ADMIN-only order search, filter and details | OrderIT + browser | Passed |
| ADM-ORD-02 | Allowed transitions, history and stale-update conflict | PostgreSQL OrderIT | Passed |
| ADM-ORD-03 | Cancellation restores once, including concurrent retries | OrderIT + browser stock 11 → 12 | Passed |
| ADM-ORD-04 | Multi-line overflow restoration rolls back fully | PostgreSQL OrderIT | Passed |


## Dashboard stage

| ID | Requirement | Evidence | State |
|---|---|---|---|
| DASH-01 | ADMIN-only bounded date/stock filters | OrderIT | Passed |
| DASH-02 | Jerusalem date boundaries, cancellation exclusion and zero days | PostgreSQL OrderIT | Passed |
| DASH-03 | Active current low stock, including zero | OrderIT + browser threshold 0 | Passed |
| DASH-04 | Poll updates after order/cancellation | Browser 1413 → 1423 → 1413 | Passed |
| DASH-05 | Stock correction competing with checkout | PostgreSQL OrderIT | Passed |


## Usability and Selenium smoke

| ID | Requirement | Evidence | State |
|---|---|---|---|
| KEY-01 | Skip link, route focus, dialog cycling and Escape | DOM + manual browser + Selenium | Passed for sampled journeys |
| FORM-01 | Labeled auth, focused error, one pending submit | React/jsdom + Selenium invalid login | Passed |
| NET-01 | Failed dashboard refresh retains data and warns | React/jsdom fetch failure | Passed |
| SEL-01 | Three languages and exact desktop/mobile widths | StorefrontTest parameterized cases | Passed |
| SEL-02 | Failure screenshot before browser teardown | Explicit isolated DiagnosticsProbe | Verified expected failure |
| SEL-03 | Checkout/customer/admin browser journeys | PurchaseE2ETest in disposable schema | Passed |


## Isolated purchase browser suite

| ID | Requirement | Evidence | State |
|---|---|---|---|
| E2E-01 | Signup/login, resumed add, quantities, shipping and checkout | 1280px and 390px parameterized case | Passed |
| E2E-02 | Stale second browser cannot add the last sold unit | Independent customer sessions | Passed |
| E2E-03 | Changed prices reviewed; order snapshot immutable | Browser + authenticated API fixture | Passed |
| E2E-04 | Sold-out/deactivated item blocks checkout and remains removable | Browser + isolated inventory fixtures | Passed |
| E2E-05 | Admin sees purchase and cancels; customer sees status and stock restored | Two WebDrivers | Passed |
| E2E-06 | Isolated setup/cleanup preserves regular demo | Schema count 0; fixture count in public 0 | Passed |


## CI gates

Accessibility matrix: `AccessibilityE2ETest` samples 17 rendered states in three
languages and two Chrome viewports (102 samples), checks axe rules, overflow and
checkout Tab order. The six-case targeted run passed after label/landmark/wrapping
fixes; hosted full-suite results are linked on Actions. Incomplete checks and
remaining human review are documented in ACCESSIBILITY_MATRIX.md.

| ID | Requirement | Evidence | State |
|---|---|---|---|
| CI-01 | Valid workflow with immutable Action pins and bounded permissions | actionlint + GitHub workflow | Configured and validated |
| CI-02 | Source secret and private/generated path checks | Gitleaks + 3 policy regressions | Passed locally; hosted security job passed |
| CI-03 | Frontend lint/types/tests/build/audit | Local rehearsal + hosted frontend job | Passed |
| CI-04 | Java and real PostgreSQL | Local rehearsal + hosted backend job | Passed |
| CI-05 | Full isolated Selenium with reports, failure screenshots and cleanup | Latest PR head run on Actions | Live status linked in CI.md |
