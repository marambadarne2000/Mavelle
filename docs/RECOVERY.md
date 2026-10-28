# Local backup and recovery

These PowerShell 7 tools target the portable PostgreSQL 17 local demo only.
Start it first with `./scripts/Start-Local.ps1`. They use ignored
`.runtime/local.json` for credentials without displaying passwords. They are not
a production backup service and do not cover Docker volumes.

## Create a backup

```powershell
$backup = ./scripts/Backup-Local.ps1
$backup.Manifest
```

Each backup has its own timestamp/UUID directory in `.runtime/backups/`, with a
compressed PostgreSQL custom archive and a SHA-256 manifest. The archive contains
the application's **public schema**: tables, rows, migrations, sequence values,
constraints and indexes. Disposable E2E schemas are excluded. It does not contain
PostgreSQL roles/passwords, `.runtime/local.json`, server configuration, browser
sessions/local storage or application source. Keep the configuration separately
if recovering the same installation. [pg_dump](https://www.postgresql.org/docs/17/app-pgdump.html)
creates a consistent database snapshot while normal activity continues.

Backups include account password hashes, customer details and shipping snapshots.
They stay in ignored local runtime folders: never commit them, attach them to a
PR, upload them as CI artifacts or put them in a publicly served directory.
They are not encrypted by this script. Before public operation, define encrypted
off-machine storage, access permissions, retention, recovery objectives and a
scheduled verification policy. No schedule or external upload is configured.

## Restore into a new isolated database

```powershell
$restored = ./scripts/Restore-Backup.ps1 -Manifest $backup.Manifest
$restored.Database
```

The tool verifies the manifest and archive checksum **before** creating a target.
Only manifests under this project's `.runtime/backups/` are accepted. It always
generates a new `recovery_<32 hexadecimal characters>` database, owned by the
local application role. There is no destination argument, overwrite option,
`--clean` or `--create` passed to pg_restore. The normal application connection
is unchanged. A failed restore removes only the newly created target. A successful
manual restore retains it for inspection; the returned name is the database to
inspect. Do not expose this copied database or its customer data publicly.

The archive recreates `public`, so the empty default public schema is removed
only inside that newly created target. [pg_restore](https://www.postgresql.org/docs/17/app-pgrestore.html)
runs with `--exit-on-error --single-transaction --no-owner --no-acl`. Use only
archives from this trusted local installation; a checksum detects changes but
does not authenticate the origin of an arbitrary archive.

## Verify and clean automatically

```powershell
./scripts/Test-RecoveryGuards.ps1
./scripts/Test-Recovery.ps1
```

The guard check verifies rejection of a modified archive, an archive path escape
and a manifest outside the backup directory without connecting to PostgreSQL.
It also runs in CI. Publication policy rejects `.dump`, `.backup` and `.tar`
files even if mistakenly placed outside ignored runtime directories.

The real local recovery test:

1. Fingerprints all public application rows, migrations, sequence values and
   constraint structure using the application role, without printing row data.
2. Creates a backup and restores into a newly generated database.
3. Compares the restored data against the unchanged source. If someone edits or
   orders during the comparison, the equality test is inconclusive and fails;
   rerun it while users are idle. The backup itself remains a consistent snapshot.
4. Checks that valid synthetic user insertion works, then requires the exact
   role/status CHECK constraints and order/user foreign key to reject three
   invalid writes. These probes run only in the recovery database and roll back.
5. Removes only the generated recovery database in `finally`, retains the backup,
   and writes a private `.runtime/recovery/<run>/result.json` with cleanup status.

Table fingerprints use ordered row hashes for equality, not cryptographic
authentication. PostgreSQL can deparse equivalent CHECK casts differently after
restore, so CHECK text is not compared verbatim: constraint structure and
validation flags are compared, and role/status behavior is tested directly.
Non-CHECK definitions are also compared. This is not exhaustive testing of every
possible database rule. Recovery targets are disconnected between commands;
the normal database/server is not stopped or redirected by the test.

There is deliberately no automatic cutover to the recovered copy. A real
incident needs an explicit decision about the destination, credentials, backup
age and any writes since the backup before reconnecting an application.
