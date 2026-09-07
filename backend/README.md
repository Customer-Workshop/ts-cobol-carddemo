# CardDemo backend (target state)

Java 21 / Spring Boot 3.4 / Maven / PostgreSQL 16 / Flyway. Target-state contract:
`functional/CardDemo/CardDemo_target_state.md` (CORE + ONLINE + DATA/BOUNDARY). Stream S-01
AccountView plan: `functional/CardDemo/AccountView_migration_plan.md`.

## Build

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -B clean verify
```

Unit tests run on H2 (`MODE=PostgreSQL`, profile `test`, unit-only). Repository integration tests run on
PostgreSQL 16 through Testcontainers (Docker required; the container picks its own port).

## Run

Default profile expects PostgreSQL at `jdbc:postgresql://localhost:5433/carddemo`
(`CARDDEMO_DB_USER` / `CARDDEMO_DB_PASSWORD`), `ddl-auto=validate`, Flyway on, no data seeding.

One-shot legacy import (never runs by default):

```bash
CARDDEMO_DB_PASSWORD=... mvn spring-boot:run -Dspring-boot.run.profiles=import \
  -Dspring-boot.run.arguments="--spring.main.web-application-type=none"
```

It loads `app/data/ASCII/{acctdata,custdata,cardxref}.txt` and the IBM037 `app/data/EBCDIC/AWS.M2.CARDDEMO.USRSEC.PS`
into `accounts`, `customers`, `card_xrefs`, `users`, and enforces the Q-10 date gate (every date column must be
`YYYY-MM-DD`; any offender aborts the import and is logged).

## Program -> endpoint map (ONLINE drift rule 1)

| Legacy program | Trancode / map | Target endpoint | Status |
|---|---|---|---|
| `CSUTLDTC` | none (CALLed utility) | none — internal `DateValidationService` (B-0014) | wave 1: ported |
| `COSGN00C` | `CC00` / `COSGN00` | `POST /api/auth/signon`, `GET /api/auth/session`, `POST /api/auth/signoff`, `GET /api/auth/header` (B-0030 header fields) | wave 2: ported (`AuthService`, `AuthController`) |
| `COMEN01C` | `CM00` / `COMEN01` | `GET /api/menu` (header + 11 options), `POST /api/menu/select` (B-0010 dispatch) | wave 3: ported (`MenuService`, `MenuController`) |
| `COACTVWC` | `CAVW` / `COACTVW` | `GET /api/accounts/{acctId}`, `GET /api/accounts/` (blank filter) | wave 4: ported (`AccountViewService`, `AccountController`) |

Wave 1 delivers no HTTP endpoints: only the data seams (Flyway `V1__account_view_schema.sql`, entities,
repositories), the session-context model (`SessionContext`), the error model (`GlobalExceptionHandler`,
`CobolApiException`, `ErrorResponse`, `CobolMessages`), the `import` profile and the CSUTLDTC port.

Wave 2 (sign-on, `COSGN00C`): `POST /api/auth/signon` `{userId, password}` -> 200 `{userId, userType, landingTarget}`
(session cookie set; `landingTarget` is `/menu` for `U` and `A`, B-0009 / Q-01), 400 `Please enter User ID ...` /
`Please enter Password ...`, 401 `User not found. Try again ...` / `Wrong Password. Try again ...`, 500
`Unable to verify the User ...`. `POST /api/auth/signoff` invalidates the session and returns the FR-08 thank-you
text. Passwords: BCrypt via `DelegatingPasswordEncoder`; a `users` row with `sec_usr_pwd_hash IS NULL` is verified
once against `sec_usr_pwd_legacy` (upper-cased input, Q-13) and rewritten as a hash with the legacy column cleared
(B-0026, D-0029). `carddemo.applid` / `carddemo.sysid` in `application.properties` feed the screen header (B-0030).

Wave 3 (main menu, `COMEN01C`): `GET /api/menu` -> 200 `{header, options[11]}` with each option
`{number, name, program, endpoint, route, implemented, userType}` ported from `COMEN02Y` (B-0033); only option 1
(`COACTVWC`) is `implemented`. `POST /api/menu/select` `{option}` -> 200 `{option, program, endpoint, route,
implemented, message}`: option 1 gives `route` `/accounts/view`, options 2-11 give `implemented:false` and
`Option not available in this release` (Q-12 / B-0032, no navigation), and a blank / non-numeric / `0` / `> 11`
option gives 400 `Please enter a valid option number...` with the normalised 2-character echo in `option`
(`'1 '`/`' 1'` -> `01`, blank -> `00`, `'A '` -> `0A`; COMEN01C.cbl:117-134). Both endpoints require the sign-on
session and answer JSON 401 without it (B-0027); Exit reuses `POST /api/auth/signoff` (the menu PF3 XCTLs to
COSGN00C without COMMAREA, so no thank-you text is displayed on this screen).

Wave 4 (account view, `COACTVWC`): `GET /api/accounts/{acctId}` -> 200 `{header, accountNumber, infoMessage,
account{10 fields}, customer{18 fields}}`, the painted map `CACTVWA` (COACTVWC.cbl:459-534) with money as
`+ZZZ,ZZZ,ZZZ.99`, SSN as `nnn-nn-nnnn`, dates as the stored 10-character text (Q-10) and the full ZIP / phone
values (DV-05). `infoMessage` is always the constant `Enter or update id of account to display`. Reads are
xref (`CXACAIX`, lowest card number wins on a multi-card account, Q-04) -> account master -> customer master, in
COBOL order. Errors carry the verbatim WS-RETURN-MSG text: 400 `No input received` (blank or `*`, hence the
empty-path mapping), 400 `Account Filter must  be a non-zero 11 digit number` (non-numeric, not 11 digits, or
zeroes; Q-02), 404 `Account:... not found in Cross ref file.  Resp:...`, 404 `Account:... not found in Acct
Master file.Resp:...` (no account or customer block, and no customer read — DV-01), 404 `CustId:... not found in
customer master.Resp: ...` **with** the account block in `account` (`AccountViewErrorResponse`), and 500
`File Error: READ     on <file>   returned RESP ...` for any other datastore failure (D-0040). RESP/RESP2 digits
are stable target values, not CICS bytes (Q-09). The endpoint requires the sign-on session (401 without it) and
never rewrites the identity (DV-02); PF3 is the SPA's route back to `/menu` (B-0011).

## Layout

```
com.carddemo.api         error model, (later) controllers + DTO records
com.carddemo.service     business logic (DateValidationService = CSUTLDTC)
com.carddemo.repository  Spring Data repositories (one per VSAM file)
com.carddemo.model       JPA entities mirroring the copybooks
com.carddemo.security    SecurityConfig (session cookie, JSON 401/403), SessionContext (COMMAREA replacement)
com.carddemo.data        CobolFieldReader, LegacyExtractParser, DateColumnGate, ImportRunner (@Profile("import"))
```
