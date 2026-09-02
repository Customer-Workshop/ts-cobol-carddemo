# S-02 Account View — Functional Requirements (`!mf_stream_fr_generation`)

Stream S-02, ONLINE, transaction `CAVW`, program `COACTVWC` (`app/cbl/COACTVWC.cbl`), map `CACTVWA`
(`app/bms/COACTVW.bms`). Cites are `cbl:` = `COACTVWC.cbl`, `bms:` = `COACTVW.bms`.
Requirements are source-derived; the target must reproduce messages byte-for-byte.

## 1. Functional requirements

| ID | Requirement | Cite | Owner |
|---|---|---|---|
| FR-S02-01 | On entry (from the main menu or direct) the View Account screen is shown with an empty Account Number field, the info line `Enter or update id of account to display`, no error, and no account/customer data. | `cbl:282-290`, `:353-360`, `:461-464`, `:528-530`, `:107-108` | UI + API |
| FR-S02-02 | ENTER with a blank account (empty, spaces, or `*`) → error `No input received`; the account field shows `*` (red); no data displayed. | `cbl:628-633`, `:653-661`, `:640-642`, `:561-565`, `:98-99` | API (message) + UI (field echo) |
| FR-S02-03 | ENTER with a non-blank account that is not exactly 11 numeric digits, or is `00000000000` → error `Account Filter must  be a non-zero 11 digit number`; entered text echoed, field red; no data. | `cbl:666-676`, `:465-466`, `:557-559`; `bms:84-90` | API + UI |
| FR-S02-04 | Valid 11-digit id with no card-xref record → error `Account:<id> not found in Cross ref file.  Resp:000000013  Reas:0000`; echoed, red; no data. | `cbl:723-758` | API + UI |
| FR-S02-05 | Xref found but no account master record → error `Account:<id> not found in Acct Master file.Resp:000000013  Reas:0000`; echoed, red; no data. | `cbl:774-807` | API + UI |
| FR-S02-06 | Account found but the xref's customer id is not in the customer master → error `CustId:<cust-id> not found in customer master.Resp: 000000013  REAS:0000000`; the **account block is still displayed**, the customer block is blank, the account field is not red. | `cbl:825-857`, `:471-491`, `:494`, `:557` | API + UI |
| FR-S02-07 | Account, xref and customer found → account block (status, opened, credit limit, expiry, cash credit limit, reissue, current balance, cycle credit, group, cycle debit) and customer block (customer id, SSN, DOB, FICO, first/middle/last name, address 1/2, state, city, zip, country, phone 1/2, government id, EFT id, primary holder) displayed; the info line remains the prompt; no error. | `cbl:471-523`, `:528-530` | API + UI |
| FR-S02-08 | Amount fields are rendered as `+ZZZ,ZZZ,ZZZ.99` (15 chars): fixed sign (`+`/`-`), leading zeros and their commas suppressed to spaces, zero → `+           .00`, integer digits above 9 dropped. | `bms:118,137,156,166,187`; `cbl:477-479,485-486` | API |
| FR-S02-09 | Customer-block derivations: SSN as `nnn-nn-nnnn`; FICO as 3 digits (leading zeros kept); customer id as 9 digits; zip = first 5 chars of the 10-char zip; phones = first 13 chars of the 15-char phones; dates as `yyyy-mm-dd` text. | `cbl:495-519`; `bms:203-354` | API |
| FR-S02-10 | Customer lookup uses the customer id of the **first** xref record of the account (AIX keyed read, base-key = card number order). | `cbl:723-740`, `:708-712`; `app/csd/CARDDEMO.CSD` CXACAIX | API |
| FR-S02-11 | PF3 / Exit returns to the caller (main menu when the caller is blank or the menu). | `cbl:324-352` | UI |
| FR-S02-12 | Any AID other than ENTER and PF3 is treated as ENTER (re-submits the screen); no "invalid key" message exists in this program. | `cbl:306-314` | UI |
| FR-S02-13 | A store read failure (non-NOTFND RESP) on CXACAIX / ACCTDAT / CUSTDAT → error `File Error: READ     on <file>   returned RESP <resp>,RESP2 <resp2>` (75 chars; `<file>` padded to 9); xref/account failures leave no data; a customer failure still displays the account block. | `cbl:759-769`, `:809-819`, `:858-868`, `:121-127` | API + UI |
| FR-S02-14 | Field lengths follow the map: account input max 11 chars; output widths as in analysis §2. | `bms:84-354` | UI |
| FR-S02-15 | The screen is reachable only with an authenticated session; any user type may use it (the program has no user-type check). | target convention S01-B6; `cbl` has no `CDEMO-USRTYP` test | UI (`authGuard`) + API (`[Authorize]`) |

## 2. Validation / message catalogue (exact)

| Key | Text | Cite |
|---|---|---|
| INFO-PROMPT | `Enter or update id of account to display` | `cbl:107-108` |
| MSG-NO-INPUT | `No input received` | `cbl:98-99` |
| MSG-ACCT-FILTER | `Account Filter must  be a non-zero 11 digit number` | `cbl:672` |
| MSG-XREF-NOTFND | `Account:` + id(11) + ` not found in` + ` Cross ref file.  Resp:` + `000000013 ` + ` Reas:` + `0000` | `cbl:746-756` |
| MSG-ACCT-NOTFND | `Account:` + id(11) + ` not found in` + ` Acct Master file.Resp:` + `000000013 ` + ` Reas:` + `0000` | `cbl:794-805` |
| MSG-CUST-NOTFND | `CustId:` + id(9) + ` not found` + ` in customer master.Resp: ` + `000000013 ` + ` REAS:` + `0000000` | `cbl:844-855` |
| MSG-FILE-ERROR | `File Error: ` + `READ    ` + ` on ` + file(9) + ` returned RESP ` + resp(10) + `,RESP2 ` + resp2(10) | `cbl:121-127`, `:761-769` |

Derivation of the numeric suffixes: `ERROR-RESP`/`ERROR-RESP2` are `X(10)` receiving `S9(09) COMP`
(`cbl:36-37,56-59`) → 9 unsigned digits + 1 space; NOTFND gives RESP 13 / RESP2 80; the STRING into
`X(75)` truncates the tail (`Reas:0000` = first 4 of `000000080 `; `REAS:0000000` = first 7).

Dead text (never SET, not requirements): `Account number must be a non zero 11 digit number`
(`cbl:104-105`), `Displaying details of given Account` (`cbl:111-112`), `Account number not provided`
(`cbl:101-102` — SET at `:657` but overwritten at `:641` before display).

## 3. Field / data derivations

See analysis §2 (screen fields) and §7 (data dictionary). All amounts `decimal`; dates `DateOnly?`
rendered `yyyy-MM-dd` (blank when null); alphanumerics truncated to map width.

## 4. Acceptance criteria (test matrix)

| FR | Backend test | Frontend spec |
|---|---|---|
| 01 | service: initial state builder returns prompt | renders empty field + prompt, no error, no data |
| 02 | `""`, `"   "`, `"*"` → `No input received`, echo `*`, filter Blank | blank submit shows `*` red + message |
| 03 | `"123"`, `"1234567890a"`, `"00000000000"`, `"0000000001 "` → filter message | submit echoes text, red, message |
| 04 | xref null → MSG-XREF-NOTFND, no repositories beyond xref called | message rendered, no blocks |
| 05 | xref found, account null → MSG-ACCT-NOTFND | message rendered, no blocks |
| 06 | customer null → MSG-CUST-NOTFND, account block present, filter Valid | account block visible, customer blank, field not red |
| 07 | integration: seeded ASCII data `00000000001` → all fields | full render from mocked DTO |
| 08 | formatter table (0, 1940.00, -12.5, 9,999,999,999.99 truncation) | — |
| 09 | mapper: SSN/FICO/zip/phone/date | — |
| 10 | integration: two xrefs, lowest card number's customer chosen | — |
| 11 | — | Exit button and F3 navigate to `/menu` |
| 12 | — | F7 submits (same as ENTER), no invalid-key text |
| 13 | throwing repositories → MSG-FILE-ERROR per file; customer failure keeps account | 500 body message rendered |
| 14 | — | `maxlength="11"` |
| 15 | controller `[Authorize]`; 401 without token (integration) | route has `authGuard` |

## 5. Traceability

FR-S02-01..15 ↔ `programs/COACTVWC_functional_requirement.md` §4 (COACTVWC-01..15) ↔
`backend/CardDemo.Tests/Accounts/*` and `frontend/src/app/account-view/*.spec.ts`.

## 6. Program index

| Program | FR doc | FRs |
|---|---|---|
| COACTVWC | `programs/COACTVWC_functional_requirement.md` | FR-S02-01..15 |
