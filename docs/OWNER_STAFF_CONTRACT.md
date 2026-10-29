# Single owner, employees and customers

## Operating model

Maramigo is one merchant's store with brand catalogs, not a marketplace that
registers independent merchant owners. There is exactly one privileged owner
slot. Legacy role `ADMIN` now means the sole site owner; a PostgreSQL partial
unique index enforces this across the application. Public registration always
creates `CUSTOMER`. There is no public API that creates/promotes an owner.

The existing local owner is preserved, with credentials kept privately in ignored
`.runtime/OWNER_LOGIN_PRIVATE.txt` and `.runtime/local.json`. No password was
reset. For a fresh installation the explicit server-side demo bootstrap creates
the first owner only; changing bootstrap email does not add another owner or
promote a customer. Production owner provisioning needs its own controlled setup.

| Area | Route | Authority and scope |
| --- | --- | --- |
| Customer | `#login`, `#register` | Public customer registration, basket, own checkout/orders |
| Owner | `#owner-login`, `#owner` | Business details, employees/invitations, dashboard, products and all orders |
| Employee | `#staff-login`, `#staff-register/<code>`, `#staff-orders` | Invitation activation, fulfilment details and shipping/delivery updates only |

Sign-in sends a portal identifier. Correct credentials for the wrong portal
return 403 and invalidate the newly authenticated session. The underlying shared
authentication endpoint still accepts omitted portal for existing trusted test
and local smoke clients; this never changes a user's server-assigned role.
Portal selection is not a security factor; API role enforcement is authoritative.

## Owner business details

The owner can edit full name, business name, optional business registration
number, business contact email and business phone. These are separate from
customer registration. Contact email does not change sign-in credentials.
Details are self-reported, not verified proof of legal ownership. No personal
government ID image, bank account or payment card is collected by this feature.

`GET/PUT /api/admin/owner/profile` are owner-only. Updates use the authenticated
owner ID rather than a client-supplied owner/role identifier. All write operations
require CSRF. The profile is a singleton; no additional shop-owner accounts exist.

## Employee lifecycle

1. Owner creates an invitation with full name and a separate work email through
   `POST /api/admin/owner/invitations`. Existing account emails cannot be promoted.
2. Server generates 32 random bytes, returns a base64url code once and stores
   only SHA-256 of it. The code expires after 24 hours and is bound to the work
   email. Active duplicate invitations are serialized per email and rejected.
3. Owner explicitly copies a masked private link and shares it privately. The
   application sends no email/message automatically. Raw links are not listed
   again or saved to browser storage. The token is in the URL fragment rather
   than query parameters; browser history/clipboard still need private handling.
4. `POST /api/auth/staff-register` consumes the code under a row lock and creates
   `EMPLOYEE` only. Wrong email, expiry, revocation, reuse and missing CSRF fail.
   Concurrent redemption creates one account. Client-supplied role is ignored.
5. Owner can revoke unused codes or disable/restore an employee. Disabled accounts
   cannot log in; existing sessions are invalidated on their next request. A
   request already authorized/in flight is not promised to be cancelled.

Management endpoints: `GET /api/admin/owner/employees`,
`PATCH /api/admin/owner/employees/{id}` with `enabled`, and
`GET/DELETE /api/admin/owner/invitations[/{id}]`. Listing never exposes raw codes,
hashes or passwords. Access toggles accept employee IDs only, not owner/customer
IDs. Historical employees are disabled, not deleted, preserving audit references.

## Employee fulfilment API

`GET /api/staff/orders` supports bounded search/status/pagination.
`GET /api/staff/orders/{number}` returns number, status, creation time, version,
shipping name/phone/address and item SKU/name/brand/quantity. Page `total` is an
order count, never a monetary amount. DTOs exclude order totals, currency,
unit/line prices, payment data and dashboard metrics. Public catalog prices remain
public; employees cannot change them.

`PATCH /api/staff/orders/{number}/status` accepts version, nonblank reason and
`SHIPPED`/`DELIVERED` only. Existing transactional order rules still require
PENDING → SHIPPED → DELIVERED, reject stale versions and record the actual
employee user ID in history. Cancellation, inventory edits, prices, staff
management and financial dashboards require the owner APIs and are forbidden to
employees. Employees cannot use the customer checkout/orders API with that role.

## Money remains demo-only

There is no collection, payout or real income in this build. Dashboard values
are explicitly demo order values. For live sales, payment collection must be
connected to the owner's merchant account with the provider's identity/business
verification, secrets and webhooks. Employee registration never adds a payout
recipient. No settlement bank details or provider account were configured here.

## Verified boundaries and limits

Seven real PostgreSQL/HTTP tests cover the single-owner index, non-escalating
public registration, hashed/email-bound one-time invitations, expiry/revocation,
concurrent use, wrong-portal session cleanup, employee disabling and financial
redaction/allowed fulfilment transitions. Two desktop/mobile browser journeys
cover profile save, invitation activation, fulfilment, customer tracking and
revocation without changing normal demo data. The accessibility matrix adds owner
and employee entry/registration plus the owner workspace in all three languages.

MFA, email delivery/verification, owner password recovery/transfer, invitation
delivery infrastructure and real payment onboarding are separate future work.
Business details and possession of an invitation do not prove legal identity.
