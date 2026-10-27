# Accounts and authorization — implementation contract

Implemented after the catalog/database/browser gate passed. Verified with real PostgreSQL/HTTP integration tests and local customer UI registration/login/logout.

## Session-based API

- `GET /api/auth/csrf`: public, returns `{ token, headerName }`. The token is stored in the HTTP session. Fetch it before state-changing requests and again after login/logout.
- `POST /api/auth/register`: CSRF-protected JSON `{ email, password }`; creates CUSTOMER only; 201 with `{ id, email, role }`. No automatic login. Duplicate normalized email → 409; invalid input → 400. Unknown JSON fields must not grant privileges.
- `POST /api/auth/login`: CSRF-protected `application/x-www-form-urlencoded`, fields `username` (email) and `password`. Success → 200 user view, failure → 401 generic message. Spring Security manages session fixation protection and the security context.
- `GET /api/auth/me`: 200 user view when authenticated, otherwise 401.
- `POST /api/auth/logout`: CSRF-protected; invalidates session, clears security context and cookie; 204. Refresh CSRF before a later login.
- `GET /api/admin/identity`: authenticated ADMIN only, returns the administrator user view. This minimal gate proves role enforcement before administrative product writes exist.

Email is trimmed and lowercased using Locale.ROOT; max 254 characters, ASCII format with a dotted domain, unique database constraint. Password: at least 12 Unicode characters and at most 72 UTF-8 bytes, no trimming or silent truncation, hashed with BCrypt cost 12. Registration responses and application logs never include passwords or hashes. No password reset endpoint is in scope.

Only catalog GETs, health, registration, login and CSRF are public. All other API routes require authentication, and `/api/admin/**` requires ADMIN. Authorization is enforced on the server. UI visibility is supplementary.

Same-origin proxy avoids CORS configuration. Cookie: HttpOnly, SameSite=Lax; Secure is required for HTTPS deployment and disabled explicitly in the local HTTP profile. Public registration cannot assign roles.

Demo admin initialization is conditional on explicit environment variables and the demo profile. No password is embedded in source. An existing account must never be silently promoted or have its password reset by bootstrap.

Login requests are limited per remote address with bounded memory and a finite time window. Do not trust client-provided forwarding headers without a configured trusted proxy. Include a Retry-After response on 429. Public hosting needs a shared limiter when multiple instances are introduced.

## Required evidence

Real HTTP sessions with separate cookie stores and a real PostgreSQL schema:

1. Anonymous `/me` → 401; anonymous admin route → 401.
2. Missing CSRF on registration/login/logout → 403.
3. Invalid registration → 400; valid registration → 201; duplicate normalized email → 409.
4. Stored password is a hash; no hash in API response.
5. Wrong password → 401; valid credentials create a session and rotate session ID.
6. Customer admin request → 403, even if registration attempted to request ADMIN.
7. Explicit demo admin can access the admin gate.
8. Logout invalidates access; reuse of stale CSRF fails.
9. Rate limit triggers 429 and expires at the window boundary.
10. Browser: register, login, show account identity, logout; clear error and loading states.
