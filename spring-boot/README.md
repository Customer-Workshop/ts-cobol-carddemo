# CardDemo Spring Boot port

This is an additive Java 21/Spring Boot port of CardDemo. Original COBOL, JCL,
BMS, copybooks, and data under `app/` are not modified.

## Prerequisites, build, and run

Requires a Java 21 JDK and Maven 3.9+. Use
`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` (or another Java 21 JDK) for
both Maven and packaged execution.

```bash
cd spring-boot
mvn -q clean verify
mvn -q spring-boot:run   # or: mvn -q package && java -jar target/carddemo-spring-boot-0.1.0-SNAPSHOT.jar
```

Seed location and behavior are overridable on the command line, e.g.
`--carddemo.seed.data-dir=../app/data --carddemo.seed.force=true`.

`spring.batch.job.enabled=false`; jobs are launched explicitly.

## Seeding

The default seed directory is `../app/data`. ASCII fixed-width files provide
customers, accounts, cards, cross-references, transactions, reference tables,
and disclosure groups. The `USRSEC` file is EBCDIC and defaults to IBM037.

```properties
carddemo.seed.enabled=true
carddemo.seed.data-dir=../app/data
carddemo.seed.force=false
carddemo.seed.usrsec-charset=IBM037
carddemo.seed.acctdata-group-id-in-zip-slot=true
```

Seeding is non-destructive by default; `force=true` enables a fresh development
import. The sample account file stores the group identifier in the ZIP slot,
which is handled by the explicit compatibility property. USRSEC uses a
plaintext-compatible encoder for the legacy fixture only.

## Sign-on

Seeded development users are `ADMIN001` through `ADMIN005` and `USER0001`
through `USER0005`. Their passwords are the plaintext-compatible values in the
seeded USRSEC fixture; these are not production credentials.

## REST endpoints mapped to COBOL

| Endpoint | Program |
|---|---|
| `POST /api/auth/signon`, `GET /api/auth/session`, `POST /api/auth/signoff` | `COSGN00C` |
| `GET /api/menu`, `POST /api/menu/select` | `COMEN01C` / `COMEN02Y` |
| `GET /api/admin/menu`, `POST /api/admin/menu/select` | `COADM01C` |
| `GET /api/accounts/{acctId}` | `COACTVWC` |
| `PUT /api/accounts/{accountId}` | `COACTUPC` |
| `GET /api/cards` | `COCRDLIC` |
| `GET /api/cards/{cardNumber}` | `COCRDSLC` |
| `PUT /api/cards/{cardNumber}` | `COCRDUPC` |
| `GET /api/transactions` | `COTRN00C` |
| `GET /api/transactions/{transactionId}` | `COTRN01C` |
| `POST /api/transactions` | `COTRN02C` |
| `POST /api/billing/payments` | `COBIL00C` |
| `POST /api/reports` | `CORPT00C` |
| `GET /api/admin/users` | `COUSR00C` |
| `POST /api/admin/users` | `COUSR01C` |
| `PUT /api/admin/users/{userId}` | `COUSR02C` |
| `DELETE /api/admin/users/{userId}` | `COUSR03C` |
| `POST /api/admin/jobs/{jobName}` | explicit batch administration |

Account/customer identifiers are JSON numbers. Fixed-width display codes remain
strings, including CVV and transaction type/category codes.

## Batch jobs, origins, and launch

Every job is explicitly launchable through
`POST /api/admin/jobs/{jobName}` and has a chunk-oriented step with an item
reader, processor, and writer. Flat-file readers are used for daily
transactions and export/import; repository readers supply VSAM-equivalent data.

| Job / step | COBOL/JCL origin | Output |
|---|---|---|
| `cbtrn01Job` / `cbtrn01Step` | `CBTRN01C`, daily validation JCL | `cbtrn01-validation.txt` |
| `cbtrn02Job` / `cbtrn02Step` | `CBTRN02C`, POSTTRAN JCL | `cbtrn02-rejects.txt` |
| `cbtrn03Job` / `cbtrn03Step` | `CBTRN03C`, report JCL | `cbtrn03-report.txt` |
| `cbact04Job` / `cbact04Step` | `CBACT04C`, INTCALC JCL | interest transactions/accounts |
| `cbstm03Job` / `cbstm03Step` | `CBSTM03A`/`CBSTM03B`, `CREASTMT.JCL` | `STATEMNT.PS`, `STATEMNT.HTML` |
| `cbexportJob` / `cbexportStep` | `CBEXPORT`, export JCL | `EXPORT.DATA` |
| `cbimportJob` / `cbimportStep` | `CBIMPORT`, import JCL | normalized rows, `CBIMPORT.errors` |

`CORPT00C` validates the request, launches `cbtrn03Job` with `startDate` and
`endDate`, and returns the Spring Batch execution identity. Example:

```bash
curl -b cookies.txt -X POST \
  http://localhost:8080/api/admin/jobs/cbtrn02Job \
  -H 'Content-Type: application/json' -d '{}'
```

## Deviations and not ported

* CICS routing, XCTL chains, COMMAREA hand-off, pseudo-conversational re-entry,
  and internal-reader JCL submission are represented by HTTP DTOs, session
  state, and explicit `JobLauncher` calls.
* BMS attributes, cursor positioning, PF keys, map protection, and screen
  navigation are not applicable to JSON REST.
* USRSEC plaintext-compatible handling is retained for legacy fixture
  compatibility only. CSRF is disabled for the JSON API.
* The H2 console is disabled by default; if enabled for local diagnostics, it
  is restricted to admin sessions and should never be exposed beyond localhost.
* `COCRDUPC` edits expiry month/year; the existing day is preserved where
  possible and clamped when the new month is shorter.
* `CBSTM03B` is mapped to repository readers for keyed customer/account and
  cross-reference access plus transaction aggregation, not an endpoint.
* COMP/COMP-3 values are typed Java values. Export records retain 500-character
  fixed boundaries and type-specific fixed-width payloads; binary COMP fields
  use fixed-width textual equivalents.
* Spring Batch chunk transactions replace COBOL file commit boundaries.
  Interest groups category balances by account, so balance and cycle reset
  occur once at the control break.
* CICS/BMS-only display and abend behavior is not exposed as REST responses.
* Statement HTML escapes customer, transaction, and merchant text supplied by
  free-text input; this is a safety behavior and is not byte-identical to the
  CBSTM03B output.
