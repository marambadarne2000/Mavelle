# Dependency security review

Run from the repository root with Java 17, Maven, Python 3 and network access:

```powershell
./scripts/Audit-Java.ps1
```

The launcher resolves the actual transitive dependency trees for both `backend`
and `automation`, including test dependencies. The local application coordinates
are excluded because they are not published Maven packages. Maven dependency
plugin 3.8.1 produces JSON; the scanner queries the official [OSV batch API](https://google.github.io/osv.dev/post-v1-querybatch/)
with public Maven package names and versions only. It does not send source,
credentials, configuration or customer data. It handles pagination and fails on
resolution failures, network errors, incomplete responses or unreviewed findings.
This is a known-vulnerability inventory scan, not a penetration test or proof of
absence of vulnerabilities. Maven build plugins, Java itself, Chrome, PostgreSQL
server and OS packages need their own maintenance reviews.

The timestamped report is `artifacts/security/java-osv.json` (ignored locally,
uploaded with the backend CI reports for seven days). Every detected finding is
retained, including reviewed findings. A passing result with reviews explicitly
says **PASS WITH REVIEWS**, never zero vulnerabilities. No severity is silently
filtered. Five scanner regressions cover transitive inventory, pagination,
incomplete responses, repeated tokens, and exact review matching/expiry.

## Maintenance updates on 2026-10-27

| Library | Previous | Updated |
| --- | --- | --- |
| Jackson BOM (backend and automation) | 2.21.4 | 2.21.8 |
| Embedded Tomcat | 10.1.55 | 10.1.61 |
| PostgreSQL JDBC | 42.7.11 | 42.7.14 |
| Log4j BOM | 2.24.3 | 2.25.5 |
| Commons Compress (Testcontainers test dependency) | 1.24.0 | 1.28.0 |
| Commons Lang (transitive test dependency) | 3.17.0 | 3.21.0 |

Jackson components are aligned by BOM. These stay on compatible maintenance
lines, with no Java or Spring major-version migration. Release existence was
verified against [Maven Central](https://repo.maven.apache.org/maven2/).

## Findings retained for explicit review

Spring MVC 6.2.19 has two known findings whose affected rendering paths are not
used by this JSON API. As of the review, Maven Central has no newer 6.2 release.
Spring 7 requires a separate compatibility migration; do not mix a Spring 7 JAR
into the current Boot 3 application.

| Finding | Assessment for this application |
| --- | --- |
| [GHSA-j9f9-w8pj-32f8](https://osv.dev/vulnerability/GHSA-j9f9-w8pj-32f8) | SSE fragment rendering is unused: no `SseEmitter`, `ServerSentEvent`, event-stream responses or server-rendered fragments. |
| [GHSA-pc63-qcmh-9cmg](https://osv.dev/vulnerability/GHSA-pc63-qcmh-9cmg) | No `XsltView`, XSLT templates or view resolver; controllers return JSON and the frontend is React. |

The source review covers `backend/src/main/java` and controller configuration.
Both reviews are limited to the exact package, version and advisory in
`security/dependency-reviews.json`, expiring **2026-11-27**. An expired review
fails the scan. Reassess sooner when adding SSE, XML transformations, views or
changing dependencies. These reviews assess reachability, not a patched library
or a claim that the application is safe for unrestricted public deployment.

Frontend dependency review remains `npm audit --audit-level=high` against the
official npm registry. Source secrets use Gitleaks with redacted output. Neither
scan replaces authentication, transaction and browser acceptance tests.
