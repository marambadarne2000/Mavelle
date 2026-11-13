# Page navigation and home discovery — 31 October 2026

Every application route, including sign-in, business registration and not-found
recovery, renders a sticky, keyboard-accessible page toolbar. Back returns to the
previous internal page visited in this tab, preserving repeated visits; opening
a page directly falls back to Home. The trail is bounded to 50 entries, stays in
memory and strips employee invitation tokens. It never sends visitors to an
external site. Refresh starts a new trail; this is an application navigation
control rather than a persisted browser-history database.

The dashboard shortcut uses the authenticated server role: CUSTOMER → `#account`,
MERCHANT → `#merchant`, ADMIN → `#marketplace-admin`, EMPLOYEE → `#staff-orders`.
Guests receive a sign-in link. Existing API authorization still applies; a link
does not grant privileges. Anchored sections have clearance below the toolbar.

Home now combines an automatically rotating spotlight, collection suggestions,
approved-store links and current public store announcements. Carousel changes
every 4.5 seconds, offers previous/next and pause/resume controls, pauses while
hovered or keyboard-focused and does not advance in a hidden tab. Reduced-motion
preferences disable automatic rotation and CSS movement; manual controls remain.
Focus stays on controls, and automatic changes are not repeatedly announced by
screen readers. Decorative floating rings do not convey essential information.

Recommendations use the current public collection, not behavioral profiling or
invented popularity statistics. Brand catalogs and prices come from existing
APIs. Announcements are seller-provided promotions, not third-party advertising
or paid-placement claims. Demo stores remain labeled. Data refreshes every two
minutes while visible; partial fetch failures retain useful content and expose
retry. Empty announcements do not create fake discounts or ads.

`GET /api/discovery/announcements` is public, bounded to 12 and excludes expired,
future, non-approved-store and non-public announcements. Migration V9 sets
`public_advertisement=false` for all existing rows. Publishing on Home requires
explicit per-announcement merchant opt-in; otherwise the original purchased-store
customer feed retains its existing audience. The editor displays each item's
audience and resets the public opt-in after submission. Public DTOs contain only
the opted-in content, public store identifiers and expiry, not buyer details,
business registration numbers or private business contacts.

Verification adds a real PostgreSQL/HTTP public/private/expiry/suspension test,
four UI cases for back navigation, role shortcuts, manual/reduced motion and
pause/resume/focus, and a mobile browser navigation case. Marketplace browser
journeys also exercise explicit public publication and Home display. The suite
now comprises 56 backend + 35 frontend + 24 Selenium = 115 tests; results for a
specific revision must come from the matching run, not an old report. Existing
multilingual accessibility coverage includes the new shared navigation and Home.

Audit update: the decorative ring cycle is 5.5 seconds and slide entrance is 0.5 seconds. Pause, hover/focus and reduced-motion behavior remain intact.
