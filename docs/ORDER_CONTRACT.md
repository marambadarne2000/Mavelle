> Current checkout supports optional `demoPaymentOutcome: SUCCESS | DECLINED`. The storefront sends it; omitted values preserve legacy reservation-only requests. Decline creates no order/stock movement. Success records the order, stock and simulated capture together. A later simulated payment charges only still-active store parts. Returns, partial cancellation and finance snapshots: [connected portfolio contract](PORTFOLIO_RELEASE_HE.md).

# Demo checkout and orders

`POST /api/orders` requires an authenticated session, CSRF token and an `Idempotency-Key` (16–64 alphanumeric, underscore or hyphen characters). The JSON body contains 1–50 distinct `items` with `productId`, integer `quantity` (1–99), and `expectedPrice`, plus `shipping` (`fullName`, `phone`, `address`, `city`, `postalCode`, uppercase two-letter `country`). Server validation bounds all strings. No payment gateway or real charge exists.

The server uses actual database prices and rejects stale expected prices, inactive products and insufficient stock. It locks products in ID order and creates the order, immutable item snapshots, inventory movements and stock deductions in one transaction. A failure rolls back every line. Snapshots retain SKU, product name, company name and unit price at purchase time.

A transaction-scoped advisory lock serializes each user/key pair. Repeating the same normalized payload returns the original order without another stock deduction. Reusing the key with different details returns 409. Concurrent requests for the last unit cannot both succeed.

`GET /api/orders?page=0` returns the current user's orders, 20 per page. `GET /api/orders/{number}` returns only an owned order; unknown and other-user orders return 404. Public order creation and unauthenticated reads are rejected. Initial status is PENDING. Administrator status changes and cancellation are implemented separately; see ADMIN_ORDER_CONTRACT.md.

The UI reads current prices before submission and displays success only after the server confirms a valid order number and items. It removes only the quantities purchased, preserving later basket additions. Network timeouts, unreadable success responses and HTTP 5xx leave the basket intact and offer a retry of the same frozen request and key. The key and payload digest may be stored in sessionStorage; shipping details remain in memory until sent to the local server and are not stored in browser persistence. A refresh loses the in-memory shipping form; My orders allows checking an uncertain result.

Confirmation details can be revisited and printed. They explicitly state that the document is a demo confirmation, not a tax invoice. Shipping charges and taxes are not calculated.

PostgreSQL integration tests cover ownership, authentication/CSRF, invalid quantities and duplicate lines, price snapshots, stale prices, inactive products, multi-line rollback, concurrent last-unit checkout and concurrent replay. Browser verification completed a synthetic order for one MG-001 at ILS 129: stock changed from 12 to 11 and the purchased basket line cleared only after confirmation.
