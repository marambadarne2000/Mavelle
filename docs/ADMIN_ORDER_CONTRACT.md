# Administrator order management — stage 13

All `/api/admin/orders` operations require an ADMIN session. Mutations also require a current CSRF token. Customers cannot inspect other customers' details through these endpoints.

1. `GET /api/admin/orders?status=&search=&page=0` returns `{items,total,page,size}` with 20 summaries per page. Status is empty or PENDING/SHIPPED/DELIVERED/CANCELLED. Search matches an order-number substring literally, ignoring case (including literal `%` and `_`). Ordering is creation date descending with ID as tie breaker.
2. `GET /api/admin/orders/{number}` returns `{order,version,history}`. Order retains the purchase-time snapshots and shipping details. History contains from/to status, reason, administrator ID and timestamp.
3. `PATCH /api/admin/orders/{number}/status` accepts `{status,version,reason}`. Version is the nonnegative integer read from details; reason is trimmed, required and at most 240 characters.

Allowed transitions are PENDING → SHIPPED → DELIVERED and PENDING → CANCELLED. Shipped/delivered orders cannot be cancelled in this demo; returns are outside the current scope. Terminal orders cannot be reopened. Invalid transitions or stale versions return 409. Unknown orders return 404.

The transaction locks the order row before checking its current state/version. Cancellation locks its products in ascending ID order, restores original quantities (even for subsequently deactivated products), adds inventory movements, increments the order version and records history atomically. If restoration would exceed 1,000,000 stock units, the entire transaction rolls back; no partial restoration or cancellation remains. Concurrent cancellations and retries return the already cancelled order without another restoration/history entry. Concurrent dispatch and cancellation using the same version have only one winner.

The interface has order-number search, status filtering, pagination, details, shipping information, history and explicit refresh. Cancellation requires an inline checkbox explaining inventory restoration. All mutation controls are disabled while submitting. A failed/uncertain response keeps the selected order and offers reload; it never displays a successful update without a successful server response. Customer details display the resulting current status.

PostgreSQL tests cover ADMIN/customer/anonymous access, CSRF, input validation, filters, full delivery flow, stale versions, invalid transitions, cancellation of inactive products, simultaneous cancellations, cancellation versus dispatch and multi-product overflow rollback. No real shipping or payment provider is contacted.
