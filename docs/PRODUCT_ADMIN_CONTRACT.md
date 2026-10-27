# Product management — acceptance contract

Scope: authenticated ADMIN product creation, metadata edits, activation/deactivation and audited stock adjustments. CUSTOMER and anonymous callers cannot read or write administrative endpoints. Every mutation requires a fresh session CSRF token.

Implemented and verified: five PostgreSQL/HTTP integration scenarios, plus browser creation, metadata editing, stock changes, negative-stock rejection, deactivation, hidden storefront results and clearing the management view after logout. The synthetic browser product QA-BROWSER-20261027 remains inactive; original demo products were not changed.

## API

- `GET /api/admin/products?search=&page=0&size=12`: active and inactive products, search by name or SKU, stable ID order, bounded pagination.
- `GET /api/admin/products/{id}`: current administrative view.
- `POST /api/admin/products`: `{ product: { sku, name, description, category, price, active }, initialStock }`; 201. SKU is trimmed and uppercase. Initial stock creates an audit movement when positive.
- `PUT /api/admin/products/{id}`: `{ product: { ... }, version }`; metadata only, including activation. Never changes stock.
- `PATCH /api/admin/products/{id}/stock`: `{ delta, reason, version }`. Delta must be a nonzero integer, with a nonblank reason. Resulting stock must remain between zero and 1,000,000.
- `GET /api/admin/products/{id}/inventory-movements`: paginated movement history, newest first, with actor ID, quantity delta, reason and UTC time.

Administrative product views add `active` and `version` to the public product fields. A stale version returns 409 instead of overwriting a concurrent edit. Edits and stock changes lock the product row within a transaction. Stock and its movement commit together. Actor ID comes from the authenticated principal, never from JSON. There is no hard-delete endpoint.

Original demo seed quantities are baseline data and have no actor audit row. Newly created positive stock and all administrator stock adjustments are recorded. The movement table is not presented as a complete historical ledger for pre-existing seed products. Order-related movements will be added with the checkout module.

Limits: SKU 2–80 ASCII letters/digits/underscore/hyphen; name 1–160 trimmed characters; description 1–4,000; category one of Living, Wear, Accessories, Wellness; price positive, max 10 integer digits and two decimals; initial stock 0–1,000,000. Prices are submitted as decimal strings and parsed as BigDecimal on the server.

## Quality gate

Real PostgreSQL + HTTP tests must prove role/CSRF enforcement, validation, duplicate SKU rejection, update persistence, hiding inactive products, unchanged stock after metadata edits, audited stock changes, rejection of negative stock and stale edits, and one winner for simultaneous updates using the same version. Browser verification covers product creation, editing and deactivation. Test-only products remain separate from seed products.
