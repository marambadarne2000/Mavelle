# Purchase E2E verification

Run `./scripts/Test-Automation.ps1 -Full` from the project root. This runs 12 Chrome cases: six public smoke cases and six purchase cases (the desktop/mobile happy path counts twice). No skipped cases are required for success. `./scripts/Check.ps1 -Integration` runs the 38 backend and 27 frontend checks separately; together with the 12 full browser cases there are 77 verified regular checks. The optional deliberate DiagnosticsProbe failure is not included.

The purchase suite never runs against the current demo database schema. Its launcher provisions a random namespace and separate backend/Vite processes on free ports. The database role is reused, but the JDBC search path, Hibernate mapping and Flyway history are isolated. The launcher confirms its synthetic administrator exists inside the expected schema before running tests. Driver cache and application test cache are project-local and ignored. The normal application keeps running.

Fixtures are scoped to each test through unique product names and unique customer emails. Test APIs retain CSRF and ADMIN checks and use HTTP/1.1 through the dev proxy. Authentication is tested through the real UI in successful signup/login journeys; direct API registration is used only to prepare other scenarios. No live credentials, real shipping addresses or payment provider are involved.

Covered: empty basket; anonymous add requiring login and resuming once; registration; login; quantity update and reload; shipping validation; checkout and actual server order ID; exact server totals and stock; owned order history and logout guard; stale-browser last-unit rejection; price-change warning and immutable order snapshots; zero/inactive inventory; customer/admin two-browser order visibility and cancellation with stock restoration.

Page Objects: StorePage, LoginPage, BasketPage and CheckoutPage, with Ui helpers for reliable native clicks and explicit waits. Failure evidence is captured before teardown, including before-each fixture exceptions. Tests do not use fixed sleeps or blanket retries. Startup polling waits on service health and administrator bootstrap rather than assuming the health endpoint implies that seed initialization has finished.

At completion the schema is dropped, removing all synthetic users, orders, inventory movements and fixtures together. Tests do not cancel, delete or modify existing demo orders. Individual test failure still triggers process/schema cleanup; runtime logs and JUnit reports remain for diagnosis. There is no publicly accessible wipe/reset endpoint.

Reports: automation/target/full-reports. Selected browser evidence is copied to docs/screenshots/purchase-desktop.png, purchase-mobile.png and purchase-cancelled.png. These represent the isolated application; their order numbers do not exist in the normal demo after cleanup.

Remaining: running these jobs in GitHub CI, a broader browser/device matrix, more network-uncertainty E2E cases and screen-reader review. No claim of complete test coverage or complete accessibility conformance is made.
