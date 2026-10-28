# Fresh Windows setup and source-build verification

## Prerequisites

Install Git, PowerShell 7, JDK 17, Node.js 22 with npm and Maven 3.9 on PATH.
PostgreSQL is prepared separately by the checksum-verified project script.
Internet access is required for npm, Maven Central and the vendor archive.
Chrome is needed for Selenium. Docker is optional for the portable Windows
route, but required for the default Testcontainers integration route in Linux CI.

## Source to running local demo

Until PR #1 is merged, the full application is on `codex/maramigo`; `main` only
contains the initial repository setup. The repository is private and requires
your GitHub access.

```powershell
git clone --branch codex/maramigo https://github.com/marambadarne2000/Maramigo.git
cd Maramigo
npm --prefix frontend ci --registry=https://registry.npmjs.org
./scripts/Setup-PortablePostgres.ps1
./scripts/Start-Local.ps1 -Build
./scripts/Smoke-Local.ps1
```

Open http://127.0.0.1:5173/#home. Do not copy another machine's `.runtime`,
`.env`, `node_modules`, `target` or `.tools` to make the application run.
Startup generates local database credentials, applies Flyway migrations and
creates demo data/admin credentials in ignored `.runtime/local.json`.
Read that local file privately to sign in as the demo administrator. Do not
publish its contents. Reusing an existing database never resets passwords or
inventory; customer registration is available in the interface.

```powershell
./scripts/Check.ps1 -Integration
./scripts/Test-Automation.ps1 -Full
./scripts/Backup-Local.ps1
./scripts/Test-Recovery.ps1
./scripts/Stop-Local.ps1
```

The real PostgreSQL tests use temporary schemas when the local configuration is
present. Full Selenium uses separate servers and a generated schema. Neither is
a reset of the demo. Rebuild Java changes with
`./scripts/Start-Local.ps1 -Restart -Build`, which preserves the database.

## Repeatable fresh-source build

```powershell
./scripts/Test-CleanCheckout.ps1
```

This exports **committed HEAD** using `git archive` into a unique ignored
`.tools/clean-checkouts/<run>/source` directory. It rejects copied runtime/tools,
node_modules, target or dist folders, installs frontend dependencies with
`npm ci`, runs lint/types/tests/build and builds/tests the Java package.
Logs and a revision-tagged result stay beside that export. No application is
started and no database configuration or normal demo data is copied.

This deliberately reuses installed Java/Node/Maven and the explicit Maven/npm
download caches. It tests a fresh source checkout/build, **not a new physical
machine**, a new OS installation or a complete deployment. GitHub's hosted
workflow independently uses fresh checkout/runners and starts disposable
PostgreSQL/browser test environments. Both forms of evidence complement the
local restore check; neither certifies production operations.

## Troubleshooting

| Symptom | Action |
| --- | --- |
| `mvn`/Java/Node not found | Install the prerequisites, reopen PowerShell and check PATH. |
| Portable PostgreSQL missing | Run Setup-PortablePostgres; investigate a checksum mismatch rather than bypassing it. |
| 5173/8080 occupied | Stop this project's processes with Stop-Local, or identify the other owner; do not kill unrelated processes. |
| Backend unhealthy | Inspect ignored `.runtime/backend*.log` and postgres.log privately. |
| Frontend unavailable | Inspect `.runtime/frontend*.log`; run npm ci if dependencies are absent. |
| Stale JAR before full Selenium | Rebuild with Start-Local -Restart -Build, including after pom.xml changes. |
| Recovery comparison inconclusive | Retry while no user places orders or edits products; see RECOVERY.md. |
| Private GitHub clone denied | Verify account access to the private repository; do not make it public as a workaround. |

Docker Compose remains a separate documented demo route in README. It has not
been verified on the authoring machine and is not the production deployment plan.
