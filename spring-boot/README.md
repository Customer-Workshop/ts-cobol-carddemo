# CardDemo Spring Boot vertical slice

The REST slice mirrors the COBOL signon, menu, and account-view flows:

- `POST /api/auth/signon`, `GET /api/auth/session`, `POST /api/auth/signoff`
- `GET /api/menu`, `POST /api/menu/select`
- `GET /api/admin/menu`, `POST /api/admin/menu/select`
- `GET /api/accounts/{acctId}`

Authentication is session based. CSRF is disabled because these endpoints are a
JSON API, and the legacy USRSEC passwords are plaintext; the explicitly named
`UsrsecPlaintextPasswordEncoder` is not production-grade. BMS-only screen
attributes, PF keys, cursor positions, and map behavior are intentionally not
represented in the REST DTOs. Menu targets not in this vertical slice remain
listed with their original program and a `implemented: false` response.
