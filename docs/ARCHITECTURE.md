# Architecture decisions — current implementation

## Scope fixed for the first release

Hebrew/Arabic/English UI with RTL/LTR, ILS, whole item quantities, no product variants, simulated checkout only.
No card information, payment processing, tax invoice, email or shipping integration.
Order totals contain product prices only. Shipping details are stored as an order snapshot.
The storefront uses black, cream and champagne with original CSS illustrations until licensed product photos are selected.

## Components

React + TypeScript → same-origin `/api` proxy → Java 17 / Spring Boot → PostgreSQL.
One backend deployment with separate catalog, accounts, inventory and orders modules.
Flyway owns schema changes; Hibernate validates and never updates schemas automatically.
Docker Compose is for local demonstration, not a production hosting configuration.

## Implemented relationship model

```mermaid
erDiagram
    COMPANIES ||--o{ PRODUCTS : offers
    USERS ||--o| BUSINESS_PROFILE : owns
    USERS ||--o| STAFF_PROFILES : has
    USERS ||--o{ STAFF_INVITATIONS : creates
    STAFF_INVITATIONS ||--o| STAFF_PROFILES : activates
    USERS ||--o{ ORDERS : places
    ORDERS ||--|{ ORDER_ITEMS : contains
    PRODUCTS ||--o{ ORDER_ITEMS : references
    PRODUCTS ||--o{ INVENTORY_MOVEMENTS : records
    ORDERS o|--o{ INVENTORY_MOVEMENTS : causes
    USERS o|--o{ INVENTORY_MOVEMENTS : performs
```

COMPANIES, PRODUCTS, USERS, ORDERS, ORDER_ITEMS, INVENTORY_MOVEMENTS and ORDER_STATUS_HISTORY are implemented, alongside Flyway migration history. Demo catalog data is a repeatable Flyway migration with ON CONFLICT DO NOTHING, so later versioned migrations can run normally without resetting inventory. Product rows use an optimistic version and row locks for administrative edits and stock adjustments. See the feature contracts for implemented API details; local setup and recovery are documented in FRESH_SETUP.md and RECOVERY.md.

## Contracts fixed before implementing checkout

- Browser cart stores IDs and quantities. The server is the authority for prices and stock.
- Checkout requires authentication. Session cookies are HttpOnly; CSRF protection is required before adding state-changing endpoints.
- Lock product rows in ascending ID order; reject insufficient stock and commit order, items and movements atomically.
- Store the idempotency key and request fingerprint per user with a unique database constraint. Repeated identical requests return the original order. Changed payloads conflict.
- Copy SKU, name and unit price into order items. Money uses BigDecimal / NUMERIC(12,2), two decimal places.
- Legal state transitions: PENDING → SHIPPED → DELIVERED; PENDING → CANCELLED by admin only. Cancellation restores stock once in the same transaction.
- Client submits expected prices. Any price difference returns a conflict requiring confirmation of the new total.
- No deletion of historical products or orders. Deactivate products instead.
- Dashboard refresh: next request scheduled five seconds after the preceding request finishes, with a visible last-successful-update time; see DASHBOARD_CONTRACT.md.
- UTC storage; user-facing times explicitly formatted for Asia/Jerusalem.

## Current catalog API

`GET /api/products?search=&category=&sort=featured&page=0&size=12`

Response: `{ items: [{ id, sku, name, description, category, price, stockQuantity }], total, page, size }`.
Search is case insensitive and trimmed; maximum 100 characters. Optional category is Living, Wear, Accessories or Wellness. Sort is featured (ID), price-asc, price-desc or name, with an ID tie-breaker. Page is zero based; size 1–48.

`GET /api/products/{id}` returns an active product, otherwise 404.
`GET /actuator/health` returns minimal health status (backend port only).
Public catalog endpoints remain read-only. Accounts/session endpoints are implemented as specified in AUTH_CONTRACT.md. Administrative product writes and stock history are implemented as specified in PRODUCT_ADMIN_CONTRACT.md. Basket behavior is implemented in the frontend as specified in CART_CONTRACT.md. `GET /api/products/lookup?ids=1,2` provides current public data for 1–50 requested IDs, omitting unknown/inactive products. The server does not persist the browser basket or reserve stock. Checkout/order endpoints are implemented as specified in ORDER_CONTRACT.md; administrator status transitions, history and stock restoration are specified in ADMIN_ORDER_CONTRACT.md.

## Quality gates

Unit/MVC tests: `mvn test`. Real PostgreSQL integration tests: `mvn verify -Pintegration` (Docker or explicit TEST_DB_* connection).
Frontend: `npm test` compiles the pure TypeScript basket model and runs Node's built-in test runner; `npm run build` includes strict TypeScript checking. Selenium will be implemented after the tested purchase workflow exists.
Do not count test compilation as a passing integration test. Do not mark future features complete.
