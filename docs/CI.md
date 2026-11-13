# Continuous integration

The `Application quality gate` workflow runs on pushes, pull requests and manual dispatch. It has read-only repository permissions, immutable official Action commit pins and per-job timeouts. Superseded runs on the same ref/PR are cancelled.

| Job | Runner | Checks |
|---|---|---|
| Workflow and source safety | Ubuntu | checksum-verified actionlint, repository path policy plus 3 regression tests, redacted Gitleaks scan |
| Java and PostgreSQL integration | Ubuntu | Java unit/MVC and real PostgreSQL HTTP tests, using Testcontainers/Docker; backend/automation Maven dependency audit and five scanner regressions |
| TypeScript, lint and usability | Ubuntu | locked npm install, ESLint, TypeScript, frontend regression tests, production build and npm audit (moderate or higher fails the gate) |
| Isolated Chrome purchase journeys | Windows | current Java artifact, verified portable PostgreSQL, disposable schema/servers, Selenium functional and multilingual accessibility/responsive cases, marketplace onboarding and owner/employee fulfilment |

The browser job starts only after the other three pass. Full browser tests run for each push/PR/manual trigger; the `purchase` tag is included only by the full launcher. No repository database/admin secrets are required: credentials and test data are generated on the runner and destroyed with the disposable environment. The normal local demo is not a test destination.

JUnit reports and selected failure screenshots are retained for seven days, including on failure. The summary lists each browser suite's counts. Artifacts use explicit report/screenshot paths; `.runtime`, private JSON credentials, dependency caches and database files are never uploaded. Maven dependency and checksum-verified PostgreSQL archive caches contain no application data. PostgreSQL/process cleanup runs even after a failed test job; forced runner termination still ends the ephemeral runner.

Run the equivalent local rehearsal on the prepared Windows workspace:

```powershell
./scripts/Check-CI.ps1
# Explicitly omit browsers, without counting old browser reports:
./scripts/Check-CI.ps1 -SkipBrowser
```

The full command validates the workflow and source policy, scans for secrets, checks lint/types/dependencies, runs backend/frontend suites and starts the isolated Selenium application. It preserves the existing demo. A current backend JAR and installed npm dependencies are required; cloud steps install/build these from checkout. Application verification comprises backend, frontend and Selenium suites, plus repository/security-tool regressions. Read the reports for the exact revision and counts. Accessibility-only mode is a separate six-case run and does not count old reports as a new full-suite pass. Workflow syntax, source scanning, lint and npm audit also passed. A local rehearsal is not proof of a successful GitHub-hosted run.

Security scans have defined limits: Gitleaks checks known secret patterns, and npm audit reports registry-known dependency issues at execution time. `scripts/Audit-Java.ps1` checks resolved backend and automation dependencies with OSV, retains findings and fails on unreviewed findings or expired exact-version reviews. Two unused Spring rendering paths remain explicitly reviewed through 2026-11-27; see [dependency review](DEPENDENCY_SECURITY.md). Device/accessibility certification and production deployment review remain separate work.

Pinned tools: actionlint 1.7.12 and Gitleaks 8.30.1, with publisher SHA-256 values checked before extraction. Official Action release/commit identifiers were checked against GitHub's release and Git-reference APIs on 2026-10-27. Sources: [GitHub workflow syntax](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax), [artifact retention and uploads](https://github.com/actions/upload-artifact), [actionlint](https://github.com/rhysd/actionlint), [Gitleaks](https://github.com/gitleaks/gitleaks).

## Repository and review

Repository: https://github.com/marambadarne2000/Mavelle. The public default branch contains the portfolio application. Hosted runs and artifacts are at https://github.com/marambadarne2000/Mavelle/actions. Use the latest commit checks for the live result rather than assuming success from local logs or this document.

The first hosted purchase run exposed a stale basket element during price refresh. BasketPage now uses locator-based expected conditions that reacquire the element after rerender. This is a bounded wait for the expected DOM state, not a retry of the entire scenario.

Hosted validation also exposed the same DOM transition during product removal and an early renderer timeout while applying a media preference. Related dynamic text waits now reacquire elements; reduced motion is configured at Chromium startup and verified explicitly. Browser configuration failures close the newly created driver before propagating the original error. No security/functional assertion was removed or weakened to obtain a passing result.
