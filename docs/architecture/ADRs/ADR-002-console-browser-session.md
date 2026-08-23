# ADR-002: Console browser authentication and session contract

- Status: Accepted
- Date: 2026-08-23
- Deciders: Product owner and engineering
- Supersedes: None

## Context

The Angular console currently contains onboarding and risk shells but does not call the prediction APIs. The platform currently protects `/v1/**` with hashed tenant API keys. Those keys are suitable only for server-to-server clients: placing one in Angular code, a browser cookie readable by JavaScript, `localStorage`, or `sessionStorage` would expose a durable tenant credential to browser compromise.

The browser contract must also preserve the existing invariant that clients cannot choose a tenant by sending a tenant header. The application needs a cost-conscious session design that works with the modular monolith and PostgreSQL, without introducing Redis or a separate authentication microservice.

## Decision

### Same-origin browser boundary

The production console and platform API will use a same-origin boundary. The console may be served by `platform-service` or by a reverse proxy on the same scheme, host, and port. Production will not depend on credentialed cross-origin CORS.

During local development, the Angular development server will proxy API and authentication paths to `platform-service` so the browser still observes one origin. HTTPS is mandatory outside the explicit localhost development profile.

### Login flow

Human users will authenticate through a provider-neutral OpenID Connect client in `platform-service` using the Authorization Code flow with PKCE using `S256`. The server performs the code exchange. Transaction-specific `state` and `nonce` values remain bound to the initiating browser session, and redirect URIs use an exact allowlist.

The implicit grant and resource-owner password grant are forbidden. OAuth access tokens, refresh tokens, ID tokens, authorization codes, client secrets, and tenant API keys must never enter browser storage. Selecting the production identity provider and configuring its tenant are separate approved actions.

### Server-side session

After successful login, the browser receives only an opaque, cryptographically random session identifier. Authentication state, the internal user identifier, authorized tenant memberships, the active tenant, roles, creation time, last-use time, and expiry remain server-side.

When implemented, Spring Session JDBC will persist sessions in PostgreSQL. In-memory sessions and sticky load-balancer sessions are not the production contract. Redis is not required or approved for this purpose.

Initial session limits are configurable with these secure defaults:

- 30-minute idle timeout
- 8-hour absolute lifetime
- session identifier rotation after authentication and after any privilege or active-tenant change
- server-side invalidation on logout, membership removal, account disablement, or detected credential compromise

### Session cookie

The production session cookie is named `__Host-tm_session` and has `Secure, HttpOnly, SameSite=Lax`, `Path=/`, and no `Domain` attribute. It is never accepted through a URL, query parameter, request body, or custom JavaScript header.

`SameSite=Lax` supports the top-level OpenID Connect callback while adding defense in depth. It does not replace CSRF protection. The cookie must be regenerated after login and cleared on logout and session invalidation.

### CSRF contract

All unsafe methods authenticated by the browser session require Spring Security CSRF validation. The SPA contract uses an `XSRF-TOKEN` cookie containing no authentication or tenant data and the corresponding `X-XSRF-TOKEN` request header. The CSRF cookie may be readable by Angular; the session cookie must remain HttpOnly.

The server issues a fresh CSRF token after login and logout and returns a tenant-safe Problem Details response for a missing or invalid token. Origin and Fetch Metadata validation may be added as defense in depth, but neither replaces the CSRF token.

Machine requests authenticated only by `X-Api-Key` do not rely on ambient browser cookies and may use a separately scoped stateless security chain. CSRF exceptions must be selected by authentication mechanism and endpoint contract, not disabled globally.

### Tenant binding and authorization

The authenticated OIDC subject maps server-side to an internal user and its authorized tenant memberships. For the initial console, the server selects one unambiguous active tenant. Missing, disabled, or ambiguous membership fails closed.

The active tenant is placed into the same immutable `TenantContext` used by application services. Browser-provided tenant headers, query parameters, route values, cookie values, and browser storage are never authority for tenant selection. A future multi-tenant switch must be a CSRF-protected, audited server operation that verifies membership before rotating the session identifier.

The existing hashed `X-Api-Key` mechanism remains for machine-to-machine integrations until separately changed. The Angular console must never read, persist, or send a tenant API key.

### API and logging behavior

- Browser API requests return 401 when no valid session exists and 403 when the authenticated principal lacks permission; JSON APIs do not silently redirect to an HTML login page.
- Login initiation may redirect to the configured identity provider, and logout must use a CSRF-protected POST.
- Authentication events, logout, session invalidation, access denial, and future active-tenant changes are auditable.
- Session identifiers, OAuth/OIDC tokens, authorization codes, CSRF tokens, API keys, and cookie headers are never written to application logs or error responses.

## Consequences

- The console can call tenant-safe APIs without exposing long-lived credentials to JavaScript.
- PostgreSQL remains the only primary persistence dependency and sessions can survive application restarts or multiple instances without Redis.
- `platform-service` becomes the browser-facing OAuth/OIDC client and session authority; the Angular app remains an untrusted presentation client.
- A later implementation must reconcile session-authenticated humans and API-key-authenticated machines into one server-created `TenantContext` without weakening current tenant-header stripping.
- Same-origin deployment or proxying is required. A separately hosted cross-origin SPA would require a new ADR and threat-model review.
- Session persistence adds database cleanup, expiry, and revocation responsibilities.

## Rejected alternatives

- **Tenant API key in Angular or browser storage:** rejected because XSS, extensions, shared devices, or support tooling could exfiltrate a durable tenant credential.
- **OAuth tokens stored in `localStorage` or `sessionStorage`:** rejected because JavaScript-readable bearer tokens increase the impact of XSS.
- **Stateless authentication JWT in a JavaScript-readable cookie or header:** rejected because it complicates immediate revocation and does not remove CSRF or XSS concerns.
- **In-memory production sessions:** rejected because restarts invalidate all users and horizontal scaling would require sticky routing.
- **Redis-backed sessions:** rejected because PostgreSQL already satisfies MVP durability and ADR-001 does not approve Redis.
- **Credentialed cross-origin SPA by default:** rejected because it adds CORS, cookie, and CSRF complexity without current product value.

## Non-goals

- Implementing Spring Security, Spring Session JDBC, migrations, endpoints, or Angular API calls
- Selecting or purchasing a production identity provider
- Defining enterprise SSO, SCIM, MFA policy, RBAC details, invitations, or support impersonation
- Removing machine API-key authentication
- Adding Redis, MongoDB, a new service, or production infrastructure

## Validation required for implementation

Later coding PRs must include automated evidence for:

1. Authorization Code with PKCE success and invalid-state/nonce failure paths
2. Exact production cookie attributes and session rotation
3. CSRF success and failure for unsafe browser requests
4. 401 versus 403 JSON behavior without API redirects
5. Tenant A sessions unable to read or mutate Tenant B data, including forged tenant headers
6. API keys absent from compiled console assets and browser storage code
7. Logout, expiry, membership removal, and session revocation
8. Redaction of cookies, tokens, codes, and API keys from logs and errors

## References

- [OAuth 2.0 Security Best Current Practice, RFC 9700](https://www.rfc-editor.org/rfc/rfc9700.html)
- [Spring Security OAuth 2.0 Login](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/)
- [Spring Security CSRF protection for SPAs](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [Spring Session JDBC](https://docs.spring.io/spring-session/reference/guides/boot-jdbc.html)
- [OWASP Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html)
- [OWASP CSRF Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)

## Follow-up

1. PR-025R: add the PostgreSQL-backed Spring Security session foundation and prove cookie, CSRF, API-error, and tenant-resolution boundaries without connecting a production identity provider.
2. A separate approved PR may add an OIDC provider adapter and local test provider.
3. PR-021 may connect the console to current prediction reads only after the session foundation is implemented and tested.
