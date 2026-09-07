Id: R-0002
Date: 2026-09-07
Requester: `!mf_stream_migration_plan` / https://partner-workshops.devinenterprise.com/sessions/7425b46a9daf4302a8ba5556dede4295
Team: Security/SSO
Stream: AccountView
Status: SENT (simulated)
Assumed answer: no corporate IdP is available for this engagement; the USRSEC-backed demo sign-on with BCrypt upgrade-on-login stays in force (D-0014) and real SSO remains deferred
Blocking: no

# Request — identity-provider enablement for the migrated sign-on (boundary B-0026)

## What we need

An OIDC client registration for the migrated CardDemo application, so that the legacy sign-on
screen (`app/cbl/COSGN00C.cbl:207-239`: 8-character user id, 8-character plaintext password held
in the `USRSEC` VSAM dataset, no lockout, no audit trail) can eventually be replaced by the
corporate identity provider rather than by a database of application-owned passwords.

Specifically:

1. Client id and client secret for a confidential web client (authorization-code flow + PKCE).
2. Redirect URIs for the three environments the engagement will use
   (`http://localhost:4200`, plus one test and one production host to be named).
3. The claim that carries the CardDemo user identity (the legacy key is an 8-character user id,
   `app/cpy/CSUSR01Y.cpy:18`) and the claim or group that distinguishes the legacy user types
   `U` (regular) and `A` (administrator) (`app/cpy/CSUSR01Y.cpy:22`, `app/cpy/COCOM01Y.cpy:26-28`).
4. Session policy: acceptable session lifetime and idle timeout, and whether single logout is required.
5. Whether the 8-character user ids must be mapped to corporate accounts, and who owns that mapping.

## Why / when

The sign-on program is a **shared** program: it is the entry of every online stream in the module,
and this stream ports it once on behalf of the whole module. Whatever identity model lands here is
inherited by S-02..S-14, so the enablement question is asked now even though the decision for this
stream is to defer.

## Deferral recorded with this request (decision D-0023)

- **Decision**: keep the `USRSEC`-backed sign-on as the identity source for S-01. Passwords are
  stored as BCrypt hashes; a legacy plaintext row is verified once against the legacy value and
  transparently re-written as a BCrypt hash on the next successful sign-on (upgrade-on-login,
  target state O2 / D-0014). Input is upper-cased before hashing and comparison so that the legacy
  case-insensitivity (`COSGN00C.cbl:132-136`) is preserved.
- **Dependency**: a corporate IdP (this request) plus a decision on user-id mapping (item 5).
- **Impact of deferring**: the target keeps an application-owned credential store. It has no
  lockout, no password rotation, no MFA and no audit trail beyond application logs — the same
  exposure as the legacy program, carried forward. Any security review of the migrated system will
  raise it. Nothing else in the stream depends on it: the seam is a single Spring Security
  `AuthenticationProvider` plus `UserDetailsService`, so swapping in OIDC later changes no service
  or controller outside the `security` package.
- **Re-entry condition (named)**: re-open B-0026 when **either** (a) this request is answered with
  a client registration, **or** (b) the first stream that needs real role enforcement is planned
  (S-11 UserAdmin, which owns `USRSEC` CRUD and the admin menu), **or** (c) any environment
  outside the demo/dev boxes is asked to hold real user data — whichever happens first. At that
  point `!mf_boundary_resolution` re-runs for B-0026 in decide mode.

## Assumption in force

No IdP. Sign-on authenticates against the `users` table loaded from `USRSEC` (request R-0001), with
the demo fixture user retained for tests and for the recorded UI pass.

## Log

- 2026-09-07 `SENT (simulated)` by `!mf_stream_migration_plan`; deferral above is in force.
