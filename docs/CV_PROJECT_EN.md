# Maramigo — CV project

**Full-Stack Development & QA Automation | Java, Spring Boot, React, TypeScript, PostgreSQL, Selenium WebDriver, JUnit, GitHub Actions**

- Built a multi-store marketplace with server-enforced customer, merchant, platform-owner and employee permissions, isolated business dashboards, catalog management and order fulfilment.
- Implemented transactional checkout, inventory locking, idempotent requests, mixed-store cancellation/returns and a simulated payment, commission, subscription and payout journal with exact integer monetary amounts.
- Developed account verification/recovery, owner TOTP authentication with encrypted secrets and single-use recovery codes, session revocation, scoped data exports and reviewed account closure.
- Created Java Selenium automation with Page Objects, PostgreSQL integration and concurrency tests, multilingual responsive/accessibility checks and CI workflows.

Use the final CI reports for any test-count claim. Do not describe the demo as live payment processing, legal tax invoicing, microservices, measured 85% E2E coverage, a security certification or a production deployment. This is a personal portfolio project, not an employment position or registered company.

## Interview walkthrough

1. Explain how an unauthenticated add-to-basket resumes after sign-in.
2. Demonstrate a declined simulated payment, then a successful order without duplicate stock deductions.
3. Show store-level fulfilment/returns and the matching refund/commission reversal.
4. Explain how locks and unique keys prevent duplicate refunds, capacity races and cross-store access.
5. Show CI, test reports, schema migrations, the tradeoffs of a modular monolith and the boundary between local simulation and real provider integration.
