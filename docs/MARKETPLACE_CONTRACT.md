# Marketplace contract — 30 October 2026

This supersedes the single-merchant operating model in OWNER_STAFF_CONTRACT.md.
The platform retains one ADMIN (Maram Abbas), invited platform EMPLOYEEs,
CUSTOMER accounts and independent MERCHANT accounts. All use the same visible
sign-in form; the returned server role determines the destination. Legacy owner
and staff login hashes remain aliases, without a role chooser. Existing accounts,
passwords, catalog and orders are preserved by migration V8.

## Business onboarding and platform moderation

`POST /api/auth/business-register` creates a new MERCHANT and PENDING company in
one transaction. It validates contact fields, category, matching passwords and
explicit demo terms acceptance, requires CSRF and hashes passwords with BCrypt.
Client role/company identifiers cannot grant ownership. An email cannot be
registered twice or silently promoted from a customer account. One merchant
owns one store in this version; multiple stores per merchant are not implemented.

The platform owner lists up to 200 recent applications through `/api/admin/stores`
and approves/suspends them with a required reason. Decisions are recorded in
`store_moderation_history`. The public company list, every catalog query, product
detail and basket lookup exclude companies that are not APPROVED. Checkout also
checks approval, so an old basket cannot bypass suspension. Existing orders
remain available for fulfilment and customer support after suspension.

Business name, contact and optional registration number are self-reported, not
government verification. No national ID document, card number/CVV or bank account
is collected. Production verification must be provided by the chosen payments
onboarding integration. Email verification and password recovery remain pending.

## Merchant isolation

All `/api/merchant/**` endpoints require MERCHANT. Every handler derives company
ownership from the authenticated user; a caller cannot select a different tenant.
Product create/edit overwrite supplied companyId with this derived company.
Product edits and inventory changes check ownership under a database row lock,
retain existing version checks and audit inventory adjustments. Creating,
publishing or changing products and publishing offers require an approved store.
Product listing is capped at 200; archive is `active=false`, not hard deletion.

`/store`: private business profile; `/summary`: own order count and demo gross
order value; `/products`: own catalog/stock; `/orders`: own order queue;
`/offers`: own announcements; `/conversations`: own customer threads.
Customer, employee and platform-owner privileges are not assigned to merchants.

## Mixed orders and fulfilment

`order_items.company_id` is a stable store snapshot. `merchant_orders` partitions
an order by this identifier. Merchant detail exposes only that store's line items
and necessary delivery address, without another store's items, order total or
other customers. Queue amounts are the sum of that store's snapshot line prices.
Moving a product later does not move historical order access.

Store fulfilment permits PENDING → SHIPPED → DELIVERED with version and reason.
Parent order locks serialize these changes with other stores and administrator
cancellation. Each store transition is audited. The parent is DELIVERED when all
parts are delivered, SHIPPED when at least one part has shipped/delivered, and
PENDING otherwise. Customer order detail shows each store's delivery status.
Existing platform cancellation is whole-order only while the parent is PENDING;
it restores inventory and synchronizes every store part. Per-store cancellation,
returns and partial refunds are not implemented.

## Customer data, favorites and relationships

All `/api/customer/**` endpoints require CUSTOMER and use the session user ID.
Profile fields are separate from login credentials. Favorites are server-persisted
and can be removed individually or cleared. They do not reserve stock; a hidden
or suspended item is marked unavailable. Lists are capped at 200.

Messages require a non-cancelled purchase from that store, and merchant replies
must match that same customer's purchase. Threads display the latest 100 messages;
there is no automatic email or real-time push. Reviews require a delivered store
part and are one per customer/store, editable and deletable by that customer.
They are currently private customer feedback, not a public review feed. Messages
are retained as support correspondence; deleting an account/message and retention
policies require a separate production privacy workflow.

Offers are dated store announcements, not automatic discounts. V9 adds explicit public Home publication; existing announcements default to the original customer audience. See DISCOVERY_NAVIGATION.md. The customer feed
includes only active announcements from APPROVED stores they bought from, limited
to 30. It refreshes every two minutes while its page is visible, with manual
refresh and errors. Prices at checkout do not change because of announcement copy.

## Commercial model and remaining production work

The user selected **monthly subscription plus transaction commission** and asked
the agent to choose initial rates. `/api/business-plan` proposes DRAFT ILS 99/month
plus 5%, inclusive of applicable VAT, with UNVERIFIED tax status and charges
disabled. These are commercial draft rates, not statutory rates. No subscription
charge, balance, collection, transfer or payout is implemented.
Gross demo order values are not revenue, merchant balances or platform commission.
Identity, bank/card capture, split settlement, recurring invoices, signed/idempotent
webhooks, reconciliation, refunds, taxes and disputes need a configured provider
and verified commercial rules. No real financial secrets belong in this repo.

The full numbered scope and rollout order are in MARKETPLACE_PLAN_HE.md. Production
also requires HTTPS deployment, mail, recovery, MFA for privileged accounts,
monitoring, rate limits for onboarding/messages and a reviewed privacy/terms flow.

## Verification

MarketplaceIT uses independent real HTTP sessions and isolated PostgreSQL schemas:
CSRF/confirmation/terms/non-escalation; duplicate registration rollback; cross-store
product writes and forged companyId; suspension across public lookup/checkout;
mixed-store fulfilment, snapshots, stale writes and customer separation; personal
profiles/favorites; purchased-store conversations; delivered-only reviews; offer
expiry/tenant restrictions and truthful billing state. Browser journeys cover
registration, approval gating, catalog, favorites, announcements, checkout and
fulfilment on desktop/mobile. Current execution results are in STATUS_HE.md.

Israeli launch sources, the draft commercial basis and remaining activation conditions: [ISRAEL_LAUNCH_READINESS_HE.md](ISRAEL_LAUNCH_READINESS_HE.md). PayMe is a candidate only, not connected or commercially approved.
