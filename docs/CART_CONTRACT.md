# Basket — stage 11

Visitors can browse products and inspect/remove previously saved basket items. Adding or increasing quantities requires sign-in; an anonymous add opens the full sign-in page and is resumed once after successful login. Checkout requires authentication and is implemented; see ORDER_CONTRACT.md. Adding an item never reserves or reduces inventory.

## State and limits

- Persist only `{ version: 1, items: [{ productId, quantity }] }` under the app-specific localStorage key `maramigo.cart.v1`.
- Maximum 50 distinct items and 99 units per item. IDs and quantities must be positive safe integers. Invalid or unsupported saved state is handled without crashing; storage failure keeps the current basket usable in memory and shows a notice.
- Merge repeated products into one line. A repeated add cannot exceed current availability or the per-item limit. Quantity changes do not silently truncate fractional values or remove items; removal is explicit.
- Stored names, prices, roles or stock values are never trusted or persisted. Use current server data for display. Unknown or deactivated items remain removable but cannot be treated as available.
- Removing an item and clearing the basket update storage. Reload restores quantities. Same-origin tabs synchronize the basket using the storage event.
- Simultaneous edits in different tabs use the latest persisted local state; this is not a server-synchronized basket. Pending adds are discarded after clearing/removing their items so a delayed response cannot re-add a removed item.

## Server lookup

`GET /api/products/lookup?ids=1,2` returns the current public DTOs for active products only, including zero-stock products. Limit: 1–50 IDs. Unknown/inactive products are omitted. The client maps the response by ID instead of relying on response order. This read-only endpoint cannot change stock or prices.

Fetch latest product data before an add and when opening/refreshing the basket; refresh when the window regains focus. Show network errors explicitly and never convert a failed request into a free or empty-price basket. Prices changing during an open session are highlighted on refresh. After a full browser reload only current prices are available because previous prices are intentionally not stored.

Display monetary arithmetic in integer agorot. Subtotal includes available lines only, with a clear note for unavailable/insufficient-stock lines. This is an estimate; checkout must recalculate and revalidate transactionally on the server.

## Acceptance gate

Automated model tests cover persistence validation, limits, duplicate merges, quantities, stock constraints and cent-based totals. PostgreSQL/HTTP tests cover lookup values, unavailable products, validation and limits. Browser checks cover add, duplicate add, quantity change, removal, empty basket, persistence after reload, unavailable stock, refreshed price/availability, and a final running UI. Do not add a nonfunctional checkout button; show a truthful development notice until stage 12 is implemented.

Gate passed locally: 14 Node/TypeScript model tests, two new PostgreSQL lookup tests within the existing backend suite, and the browser scenarios above. Customer login preserved the same three units and ILS 507 subtotal. QA-BASKET-20261027 was used for price/stock/deactivation checks and left inactive with zero stock. Original seed stock remained unchanged. Screenshots: basket.png and basket-availability.png.

Storage-denied and corrupt-state behavior are guarded in code/model tests; a forced browser storage failure was not manually injected. Network failures show an error instead of a subtotal; a forced live browser outage is not part of the completed manual checks. Full mobile/accessibility auditing and Selenium scenarios remain in their later plan stages.
