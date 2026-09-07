# `COACTVWC` — program functional requirements (`!mf_program_fr_generation`)

| Item | Value |
|---|---|
| Stream | S-01 AccountView (`functional/CardDemo/AccountView_functional_requirement.md`, approved STOP C, D-0033..D-0036) |
| Wave | **4** (`AccountView_migration_plan.md` §6, wave 4 — Account View) |
| Process type | ONLINE; surface = **screen `CACTVWA`** (mapset `COACTVW`), role stream entry / validator / read chain / display |
| Inputs consumed | stream FR §3.3, §4 (FR-11 target side, FR-13..FR-21), §5 (E-04, E-05, E-09..E-12), §6.3-§6.5, §7; analysis §3, §4.1-§4.4, §5; plan §3 (B-0001, B-0002, B-0007, B-0010, B-0027..B-0031), §9 (DV-01, DV-02, DV-03, DV-05), §10 (Q-02, Q-03, Q-04, Q-05, Q-09, Q-10, Q-11, Q-14) |
| Evidence policy | Source-derived (`app/cbl/COACTVWC.cbl`, `app/bms/COACTVW.bms`, `app/cpy-bms/COACTVW.CPY`, `app/cpy/COCOM01Y.cpy`, `app/cpy/CVACT01Y.cpy`, `app/cpy/CVCUS01Y.cpy`, `app/cpy/CVACT03Y.cpy`, `app/cpy/CSSTRPFY.cpy`); FACT vs INFERRED marked |
| Branch | `devin/1788757216-cardemo-account-view-stream` |

Section order is identical across the four program FRs of this stream.

---

## 1. Identity and role

| Item | Value | Cite |
|---|---|---|
| Program id / transaction | `COACTVWC`, trancode `CAVW`, mapset `COACTVW`, map `CACTVWA` | `app/cbl/COACTVWC.cbl:143-150`; `app/csd/CARDDEMO.CSD:317` |
| Source | `app/cbl/COACTVWC.cbl` (941 lines) | whole file |
| Role in the stream | **stream entry program, validator, business read chain, display**: renders the Account View screen, edits the account number, reads card-xref -> account -> customer, displays both blocks, and on PF3 transfers control back to the menu (**the stream hard stop**) | `:282-406`, `:628-681`, `:686-720`, `:471-523` |
| Shared program | **No — stream-private** (only S-01 reaches it: menu option 1) | stream FR §10; `COMEN02Y.cpy:25-29` |
| Target units | `service/AccountViewService`, `api/AccountController` (`GET /api/accounts/{acctId}`), repositories from wave 1 (`CardXrefRepository`, `AccountRepository`, `CustomerRepository`); Angular `account-view` component at business route `/accounts/view` | plan §6 wave 4; §2 |
| Target DTOs | `AccountViewResponse` (account block + customer block + `infoMessage` + header fields); `ErrorResponse{message}` via `CobolApiException(HttpStatus, message)` | plan §2, §6 wave 4 |

---

## 2. Trigger / caller contract

**Legacy (FACT)**

| Entry | Condition | Behaviour | Cite |
|---|---|---|---|
| `XCTL PROGRAM('COACTVWC') COMMAREA(CARDDEMO-COMMAREA)` from `COMEN01C` option 1 | `CDEMO-FROM-PROGRAM='COMEN01C'` and `CDEMO-PGM-CONTEXT=0` (`NOT CDEMO-PGM-REENTER`) | `INITIALIZE CARDDEMO-COMMAREA WS-THIS-PROGCOMMAREA` (identity wiped — see DV-02), then `WHEN CDEMO-PGM-ENTER` -> `1000-SEND-MAP` (entry state, FR-13) | `COMEN01C.cbl:177-187`; `:282-286`, `:353-360` |
| Direct `CAVW` with no COMMAREA | `EIBCALEN = 0` | same initialise + entry screen (no session check exists) | `:282`, `:462-463` |
| Pseudo-conversational re-entry | `RETURN TRANSID('CAVW') COMMAREA(WS-COMMAREA)` = `CARDDEMO-COMMAREA` + `WS-THIS-PROGCOMMAREA`; `CDEMO-PGM-REENTER` | `YYYY-STORE-PFKEY`; PF3 -> hard stop; else `2000-PROCESS-INPUTS`, edits, `9000-READ-ACCT`, `1000-SEND-MAP` | `:298-314`, `:361-374`, `:394-406` |
| Any other state | `WHEN OTHER` | `SEND TEXT 'UNEXPECTED DATA SCENARIO'`, `ABEND-CODE '0001'` — technical | `:375-382` |

COMMAREA fields **read**: `CDEMO-FROM-PROGRAM`, `CDEMO-PGM-CONTEXT` (`:283-284`), `CDEMO-FROM-TRANID`/`CDEMO-FROM-PROGRAM` for the return target (`:328-339`). **Written**: `CDEMO-ACCT-ID` (`:640`, `:673`, `:677`), `CDEMO-CUST-ID`, `CDEMO-CARD-NUM` (`:739-740`), and on PF3 `CDEMO-TO-TRANID/PROGRAM`, `CDEMO-FROM-*`, `CDEMO-USER-TYPE<-'U'`, `CDEMO-PGM-CONTEXT<-0`, `CDEMO-LAST-MAPSET/MAP` (`:328-347`). Private extension `WS-THIS-PROGCOMMAREA` (`:213-216`) carries `CC-ACCT-ID` and the edit flags between pseudo-conversational steps.

**Target trigger**: the SPA arrives at `/accounts/view` after `MenuSelectionResponse{implemented:true}` (owned by `COMEN01C`); the component renders the entry state locally (no backend call, FR-13) and on Search calls `GET /api/accounts/{acctId}` with the raw typed value. Exit (button / `F3` / `Esc`) routes to `/menu` and discards component state (FR-21). All calls require the authenticated session (401 otherwise, B-0027).

---

## 3. Inputs and outputs at field level

### 3.1 Screen `CACTVWA` — INPUT field (the only one)

| Field | Symbolic / PIC | BMS attributes | Label (verbatim) | Edit rule | Target | Cite |
|---|---|---|---|---|---|---|
| `ACCTSID` | `ACCTSIDI PIC 99999999999` (11) | `PICIN='99999999999'`, `FSET,IC,NORM,UNPROT` (no `NUM`) | `'Account Number :'` | `'*'` or spaces -> not supplied (E-04); `NOT NUMERIC` or zeroes -> E-05; otherwise accepted, echoed and used as key | path variable `{acctId}` (raw string, max 11) and component field `accountNumber`; server re-validates in COBOL order | `COACTVW.bms:83-89`; `COACTVW.CPY:55-60`; `cbl:628-633`, `:649-681` |

AID keys: ENTER = search; PF3 (and PF15 via `CSSTRPFY`) = exit; **every other key is folded to ENTER** (`:306-314`; `CSSTRPFY.cpy:34-35`, `:58-59`). Target: `Search` button / Enter-in-form, `Exit` button + `F3`/`Esc` (Q-06, DV-03).

### 3.2 Screen `CACTVWA` — DISPLAY fields -> `AccountViewResponse`

Account block (populated only when `FOUND-ACCT-IN-MASTER`, `:471-490`; field dictionary analysis §4.1, `CVACT01Y.cpy`):

| Screen field | Len / `PICOUT` | Label (verbatim) | Legacy source | Target field / type | Cite |
|---|---|---|---|---|---|
| `ACSTTUS` | 1 | `'Active Y/N: '` | `ACCT-ACTIVE-STATUS X(1)` | `activeStatus: String` | `bms:96`; `cbl:473` |
| `ADTOPEN` | 10 | `'Opened:'` | `ACCT-OPEN-DATE X(10)` | `openDate` (`LocalDate` rendered `yyyy-mm-dd`, **Q-10 gate**: raw `String` if the full extract has non-ISO values) | `bms:106`; `cbl:487` |
| `ACRDLIM` | 15, `+ZZZ,ZZZ,ZZZ.99` | `'Credit Limit        :'` | `ACCT-CREDIT-LIMIT S9(10)V99` | `creditLimit: String` formatted `+ZZZ,ZZZ,ZZZ.99` from `BigDecimal` | `bms:116-120`; `cbl:477` |
| `AEXPDT` | 10 | `'Expiry:'` | `ACCT-EXPIRAION-DATE X(10)` | `expirationDate` (Q-10 gate) | `bms:127`; `cbl:488` |
| `ACSHLIM` | 15, `+ZZZ,ZZZ,ZZZ.99` | `'Cash credit Limit   :'` | `ACCT-CASH-CREDIT-LIMIT S9(10)V99` | `cashCreditLimit` | `bms:137-141`; `cbl:479-480` |
| `AREISDT` | 10 | `'Reissue:'` | `ACCT-REISSUE-DATE X(10)` | `reissueDate` (Q-10 gate) | `bms:148`; `cbl:489` |
| `ACURBAL` | 15, `+ZZZ,ZZZ,ZZZ.99` | `'Current Balance     :'` | `ACCT-CURR-BAL S9(10)V99` | `currentBalance` | `bms:158-162`; `cbl:475` |
| `ACRCYCR` | 15, `+ZZZ,ZZZ,ZZZ.99` | `'Current Cycle Credit:'` | `ACCT-CURR-CYC-CREDIT S9(10)V99` | `currentCycleCredit` | `bms:170`; `cbl:482-483` |
| `AADDGRP` | 10 | `'Account Group:'` | `ACCT-GROUP-ID X(10)` | `groupId` | `bms:181`; `cbl:490` |
| `ACRCYDB` | 15, `+ZZZ,ZZZ,ZZZ.99` | `'Current Cycle Debit :'` | `ACCT-CURR-CYC-DEBIT S9(10)V99` | `currentCycleDebit` | `bms:191`; `cbl:485` |

Customer block (populated only when `FOUND-CUST-IN-MASTER`, `:493-523`; analysis §4.2, `CVCUS01Y.cpy`):

| Screen field | Len | Label (verbatim) | Legacy source | Target field | Cite |
|---|---|---|---|---|---|
| `ACSTNUM` | 9 | `'Customer id  :'` | `CUST-ID 9(09)` | `customerId: Long` (rendered 9 digits) | `bms:206`; `cbl:494` |
| `ACSTSSN` | 12 | `'SSN:'` | `CUST-SSN 9(09)` -> `STRING (1:3) '-' (4:2) '-' (6:4)` | `ssn: String` `nnn-nn-nnnn` | `bms:215`; `cbl:496-504` |
| `ACSTDOB` | 10 | `'Date of birth:'` | `CUST-DOB-YYYY-MM-DD X(10)` | `dateOfBirth` (Q-10 gate) | `bms:224`; `cbl:507` |
| `ACSTFCO` | 3 | `'FICO Score:'` | `CUST-FICO-CREDIT-SCORE 9(03)` | `ficoScore: Integer` | `bms:233`; `cbl:505-506` |
| `ACSFNAM`/`ACSMNAM`/`ACSLNAM` | 25 each | `'First Name'`, `'Middle Name: '`, `'Last Name : '` | `CUST-FIRST/MIDDLE/LAST-NAME X(25)` | `firstName`, `middleName`, `lastName` | `bms:242-250`; `cbl:508-510` |
| `ACSADL1`, `ACSADL2` | 50 each | `'Address:'` | `CUST-ADDR-LINE-1/2 X(50)` | `addressLine1`, `addressLine2` | `bms:267`; `cbl:511-512` |
| `ACSCITY` | 50 | `'City '` | `CUST-ADDR-LINE-3 X(50)` | `city` | `bms:300`; `cbl:513` |
| `ACSSTTE` | 2 | `'State '` | `CUST-ADDR-STATE-CD X(02)` | `stateCode` | `bms:276`; `cbl:514` |
| `ACSZIPC` | **5** | `'Zip'` | `CUST-ADDR-ZIP X(10)` | `zipCode` — **full 10 chars (DV-05)** | `bms:290`; `cbl:515` |
| `ACSCTRY` | 3 | `'Country'` | `CUST-ADDR-COUNTRY-CD X(03)` | `countryCode` | `bms:309`; `cbl:516` |
| `ACSPHN1`, `ACSPHN2` | **13** each | `'Phone 1:'`, `'Phone 2:'` | `CUST-PHONE-NUM-1/2 X(15)` | `phone1`, `phone2` — **full 15 chars (DV-05)** | `bms:318`, `:334`; `cbl:517-518` |
| `ACSGOVT` | 20 | `'Government Issued Id Ref    : '` | `CUST-GOVT-ISSUED-ID X(20)` | `governmentIssuedId` | `bms:325`; `cbl:519` |
| `ACSEFTC` | 10 | `'EFT Account Id: '` | `CUST-EFT-ACCOUNT-ID X(10)` | `eftAccountId` | `bms:341`; `cbl:520` |
| `ACSPFLG` | 1 | `'Primary Card Holder Y/N:'` | `CUST-PRI-CARD-HOLDER-IND X(01)` | `primaryCardHolder` | `bms:350`; `cbl:521-522` |

Messages and header:

| Screen field | Len | Content | Target | Cite |
|---|---|---|---|---|
| `INFOMSG` | 45 | `WS-INFO-MSG`: always `'Enter or update id of account to display'` (the `'Displaying details of given Account'` 88-level is never set — dead) | `infoMessage` constant | `cbl:110-116`, `:528-534`; stream FR §7.4 |
| `ERRMSG` | 78 (`WS-RETURN-MSG X(75)`) | E-04, E-05, E-09..E-12 or spaces | `ErrorResponse.message` | `cbl:117`, `:532` |
| `ACCTSIDO` | 11 | echo of `CC-ACCT-ID`; `'*'` in red when blank on re-enter; red when invalid | component keeps typed value; Material error styling (R-12) | `cbl:464-468`, `:553-565` |
| header / footer | — | `'Tran:'` `CAVW`, `'Prog:'` `COACTVWC`, `'Date:'` `mm/dd/yy`, `'Time:'` `hh:mm:ss`, titles, `'View Account'`, `'Customer Details'`, `'  F3=Exit '` | header fields from `Clock` + properties; `CAVW` informational only (D-0016) | `cbl:436-453`; `bms:33-78`, `:202`, `:373` |

### 3.3 Data read (field dictionary: analysis §4.1-§4.3)

| Store (boundary) | Legacy read | Key | Record | Target | Cite |
|---|---|---|---|---|---|
| `CXACAIX` (**B-0007**) | `READ DATASET('CXACAIX') RIDFLD(WS-CARD-RID-ACCT-ID-X X(11))` | account id (AIX path over `CCXREF`) | `CARD-XREF-RECORD` 50 bytes: `XREF-CARD-NUM 9(16)`, `XREF-CUST-ID 9(09)`, `XREF-ACCT-ID 9(11)` | `card_xrefs` + index on `xref_acct_id`; `CardXrefRepository#findFirstByXrefAcctIdOrderByXrefCardNumberAsc` | `:727-735`; `CVACT03Y.cpy` |
| `ACCTDAT` (**B-0001**) | `READ DATASET('ACCTDAT') RIDFLD(WS-CARD-RID-ACCT-ID-X)` | account id | `ACCOUNT-RECORD` 300 bytes (`CVACT01Y.cpy`) | `accounts`; `AccountRepository#findById(Long)` | `:776-784` |
| `CUSTDAT` (**B-0002**) | `READ DATASET('CUSTDAT') RIDFLD(WS-CARD-RID-CUST-ID-X X(09))` | `XREF-CUST-ID` from the xref | `CUSTOMER-RECORD` 500 bytes (`CVCUS01Y.cpy`) | `customers`; `CustomerRepository#findById(Long)` | `:826-834` |

### 3.4 Outputs other than the screen

| Output | Legacy | Target | Cite |
|---|---|---|---|
| Hard stop | `XCTL PROGRAM(CDEMO-TO-PROGRAM)` (= `COMEN01C`; `CDEMO-FROM-*` is always the menu in S-01) with `PGM-CONTEXT=0` | SPA route `/menu`; component state discarded; **no** session change | `:324-352` |
| COMMAREA context | `CDEMO-ACCT-ID`, `CDEMO-CUST-ID`, `CDEMO-CARD-NUM` | not persisted server-side; the response DTO carries what the screen shows (`CDEMO-CARD-NUM` is read but never displayed — not carried) | `:640`, `:673-677`, `:739-740` |

---

## 4. Functional requirements owned by this program

| Program req | Stream req | Business trigger | Observable result | Source cite |
|---|---|---|---|---|
| ACV-01 | **FR-13** (+ **FR-11 target-side entry contribution** — see split) | user arrives from menu option 1 | Account View with empty account field (focus), info line `'Enter or update id of account to display'`, no account/customer data, no error | `:282-286`, `:353-360`, `:460-469`, `:528-534`, `:543-552` |
| ACV-02 | **FR-14** | Search with account field empty or `*` | E-04 `No input received` in red; field shows `*` in red; no data | `:628-633`, `:640-642`, `:653-662`, `:561-565`; `:121-124` |
| ACV-03 | **FR-15** | account value not numeric or all zeroes (INFERRED: also < 11 digits, Q-02) | E-05 `Account Filter must  be a non-zero 11 digit number` (two spaces after `must`); field red, value echoed; no data | `:666-676`, `:557-559` |
| ACV-04 | **FR-16** | valid 11-digit number with no card cross-reference | E-09 `Account:<id> not found in Cross ref file.  Resp:<r> Reas:<r2>`; field red; no data | `:691-699`, `:737-758` |
| ACV-05 | **FR-17** | xref exists, account master record missing | E-10 `Account:<id> not found in Acct Master file.Resp:<r> Reas:<r2>`; **target: error only, no data (DV-01)** | `:701-704`, `:786-807` |
| ACV-06 | **FR-18** | xref and account exist, customer missing | E-11 `CustId:<id> not found in customer master.Resp: <r> REAS:<r2>`; account block shown, customer block blank | `:708-715`, `:836-857`, `:471-491`, `:493` |
| ACV-07 | **FR-19** | all three records exist | account block + customer block displayed (fields of §3.2), money `+ZZZ,ZZZ,ZZZ.99`, SSN `nnn-nn-nnnn`, account number echoed, no error, info line unchanged | `:737-740`, `:787-788`, `:837-838`, `:471-523`; `bms:120`, `:141`, `:162` |
| ACV-08 | **FR-20** | any of the three reads fails other than not-found | E-12 `File Error: READ on <CXACAIX |ACCTDAT |CUSTDAT > returned RESP <r>,RESP2 <r2>`; field red; no data | `:86-105`, `:759-768`, `:809-816`, `:858-865` |
| ACV-09 | **FR-21** (stream hard stop) | F3 (or F15) on Account View | main menu displayed (MEN-01 renders it); search state discarded | `:324-352`; `CSSTRPFY.cpy:34-35`, `:58-59` |

### Split ownership (exact)

| Stream req | `COACTVWC` owns | `COMEN01C` owns | Rule implemented once? |
|---|---|---|---|
| **FR-11** | the **entry state rendered on arrival** (`:282-286`, `:353-360`) — i.e. FR-13, listed here as the target side of the dispatch | option normalisation/validation, table lookup, dispatch decision (`COMEN01C.cbl:117-125`, `:177-187`) | yes: this program never inspects a menu option; the menu never renders Account View fields |
| **FR-21 / B-0010** | initiating the return: Exit -> `/menu`, discard state (`:324-352`) | re-rendering the menu once control arrives (`COMEN01C.cbl:86-92`) = MEN-01 | yes |

FR-25 (invalid key) is **not** owned here: the stream FR assigns it to SGN/MEN only, because this program folds every other AID to ENTER (`:306-314`) — no E-03 exists on this screen (stream FR §7.3). Under DV-03 the target screen has no invalid-key behaviour at all (see §7).

Not owned here: FR-01..FR-10, FR-12 (sign-on, menu); FR-22; FR-23/FR-24 (date utility — this program moves date fields as unvalidated text, `:487-489`, `:507`).

---

## 5. Business rules and validations

Order of edits is **the** wave-4 parity requirement (plan §6 wave 4: "account-id edits (E-04, E-05) in COBOL order").

| # | Rule | Behaviour | Blocking? | FACT/INF | Cite |
|---|---|---|---|---|---|
| V-1 | `'*'`/spaces normalisation | `ACCTSIDI = '*' OR SPACES` -> `CC-ACCT-ID = LOW-VALUES`; else copy | — | FACT | `:628-633` |
| V-2 | Not supplied | `CC-ACCT-ID = LOW-VALUES OR SPACES` -> `INPUT-ERROR`, `FLG-ACCTFILTER-BLANK`, `WS-PROMPT-FOR-ACCT` (`'Account number not provided'`), `CDEMO-ACCT-ID=0`; then the cross-field edit overwrites the message with `NO-SEARCH-CRITERIA-RECEIVED` = **E-04 `No input received`** (the earlier text is never shown) | blocking | FACT | `:653-662`, `:640-642`; `:119-124` |
| V-3 | Numeric, non-zero | `CC-ACCT-ID IS NOT NUMERIC OR = ZEROES` -> E-05, `CDEMO-ACCT-ID=0` | blocking | FACT; **Q-02 (INFERRED)**: <11 digits arrives left-justified with pad characters, fails `NUMERIC` -> E-05; target requires exactly 11 digits, non-zero | `:666-676`; `COACTVW.bms:84-89` |
| V-4 | Accepted | `CDEMO-ACCT-ID = CC-ACCT-ID`, `FLG-ACCTFILTER-ISVALID` | — | FACT | `:677-679` |
| V-5 | Read order | xref by account -> stop if not found/error; account by id -> (legacy continues regardless, see DV-01); customer by `XREF-CUST-ID` | — | FACT | `:686-717` |
| V-6 | Xref first-row semantics | legacy: first record on the AIX path (order undefined in repo); **target: lowest `xref_card_number` (Q-04)** | — | FACT / decision | `:727-735`; plan §3.1 B-0007 |
| V-7 | Display gating | account block only if `FOUND-ACCT-IN-MASTER` (or, legacy, `FOUND-CUST-IN-MASTER` — the quirk); customer block only if `FOUND-CUST-IN-MASTER` | — | FACT | `:471-472`, `:493` |
| V-8 | Money format | `PICOUT='+ZZZ,ZZZ,ZZZ.99'` on the six money fields | — | FACT | `bms:120`, `:141`, `:162`, `:170`, `:191` |
| V-9 | SSN format | `nnn-nn-nnnn` | — | FACT | `:496-504` |
| V-10 | Error field styling | `DFHRED` on `ACCTSIDC` when `FLG-ACCTFILTER-NOT-OK`, and `'*'` red when blank on re-enter; cursor always on the account field | — | FACT (colour constant INFERRED from IBM docs, R-12) | `:553-565` |
| V-11 | Info line | constant prompt (the "Displaying details" branch is dead) | — | FACT | `:110-116`, `:528-530` |
| V-12 | Reason codes in E-09..E-12 | RESP/RESP2 rendered as `X(10)` moves of `S9(9) COMP` (compiler-dependent digits) | — | **INFERRED** exact digits; **Q-09**: keep literal parts, append a synthesised reason code, byte parity not a goal | `:98-102`, `:744-757` |

**Target-side rules (decided)**:

- **Status mapping** (plan §2): edits -> 400 (E-04, E-05); NOTFND -> 404 (E-09, E-10, E-11); other read failure -> 500 (E-12); all with verbatim literal parts in `ErrorResponse.message`.
- **DV-01 / Q-03**: after account-master NOTFND the service **stops** and returns E-10 only; no customer read, no data blocks.
- **Q-04**: `findFirstByXrefAcctIdOrderByXrefCardNumberAsc`.
- **Q-09**: reason code appended in the legacy positions (`Resp:`/`Reas:`) as a stable target code (e.g. `0000000013`-style zero-padded, wave-4 choice), not the CICS digits.
- **Q-10 gate** (wave 1, inherited): date columns are `DATE`/`LocalDate` only if the full extract parses; otherwise raw `VARCHAR(10)`/`String`. This program renders whatever the wave-1 decision produced as a 10-character text, unchanged.
- **DV-05 / Q-11**: full `zipCode` (10) and `phone1/2` (15).
- **Q-14**: `{acctId}` presence/length validated explicitly; the untested `RECEIVE MAP RESP` (`:611-616`) is not reproduced.

---

## 6. Data access and boundaries

**Access**: three keyed reads, read-only, no unit of work, no commit/rollback (`:727-865`; analysis §5.2 — no writes anywhere in the program).

| Boundary | Register status | Decided target mechanism | Error / timeout contract | Cite |
|---|---|---|---|---|
| **B-0007** `CXACAIX` (B4 AIX path) | DECIDED | `card_xrefs` table + B-tree index on `xref_acct_id`; `CardXrefRepository#findFirstByXrefAcctIdOrderByXrefCardNumberAsc` | not found -> `CobolApiException(404, E-09)`; any other failure -> E-12 for `CXACAIX` (500). Idempotent, no retry; JDBC/pool timeout surfaces as E-12 | register B-0007; plan §3.1 |
| **B-0001** `ACCTDAT` (B4 KSDS) | DECIDED | `accounts`; `AccountRepository#findById` | not found -> 404 E-10 (**DV-01**: nothing else returned); other -> E-12 for `ACCTDAT` | register B-0001; plan §3.1 |
| **B-0002** `CUSTDAT` (B4 KSDS) | DECIDED | `customers`; `CustomerRepository#findById` | not found -> 404 E-11 **with the account block still rendered** (FR-18); other -> E-12 for `CUSTDAT` | register B-0002; plan §3.1 |
| **B-0010** hard stop (B5) | DECIDED | Exit on `/accounts/view` -> SPA `/menu`, state discarded; (the menu-side dispatch half belongs to `COMEN01C`) | n/a | register B-0010; plan §3.2 |
| **B-0027** COMMAREA (B10) | DECIDED | identity from the HTTP session; page state in `AccountViewResponse` + component; the wipe (`:283-286`) and forced `USRTYP-USER` (`:344`) are **not reproduced** (DV-02) | session expiry -> 401 -> `/signon` | register B-0027; plan §3.3 |
| **B-0028** pseudo-conversation (B5) | DECIDED | dropped; entry = local render, re-enter = `GET /api/accounts/{acctId}` | n/a | register B-0028 |
| **B-0029** BMS `CACTVWA` (B11) | DECIDED | Angular `account-view` component; labels verbatim; `PICOUT` money via DTO/pipe; `DFHRED` -> Material error styling | explicit validation (Q-14) | register B-0029 |
| **B-0030** CICS system services (B11) | DECIDED | header date/time from `Clock` (`:436-453`) | n/a | register B-0030 |
| **B-0031** abend protocol (B11) | DECIDED | `GlobalExceptionHandler` -> 500 generic `ErrorResponse`, no stack trace; nothing to back out (reads only) | 500, logged; no retry | register B-0031; plan §3.3 |

No undecided boundary for this program. (The stream analysis originally listed B-0007 as UNRESOLVED; the approved plan/register resolved it — Q-04 — and this document uses the decided state.)

---

## 7. Error and edge behavior

| Case | Legacy result | Target result | Class | Cite |
|---|---|---|---|---|
| entry from menu | prompt-only screen | local render, no API call | business (FR-13) | `:353-360` |
| account `''`, `'*'`, spaces | E-04 red, `*` red in field | 400 `No input received`; field `*` with error styling | business (FR-14) | `:628-662` |
| account `0` / `00000000000` | E-05 | 400 E-05 | business (FR-15) | `:666-676` |
| account `1234567890A`, `12 34567890` | E-05 (`NOT NUMERIC`) | 400 E-05 | business | `:666` |
| account `1234567890` (10 digits) | **INFERRED** E-05 (Q-02) | 400 E-05 | business (Q-02) | `bms:84-89`; `:666` |
| account >11 chars | impossible on the map | 400 E-05 (same message; length is part of "11 digit") | technical edge | `COACTVW.CPY:55-60` |
| valid id, no xref | E-09 | 404 E-09 (+ reason code, Q-09) | business (FR-16) | `:741-758` |
| xref ok, account missing | E-10 **plus** customer block and stale account area (defect) | 404 E-10 only — **DV-01** | deliberate deviation (FR-17) | `:789-807`, `:792`, `:704-706`, `:471-472` |
| xref+account ok, customer missing | E-11; account block shown | 404 E-11 with the account block in the payload, customer block absent | business (FR-18) | `:839-857`, `:493` |
| all found | both blocks | 200 `AccountViewResponse` | business (FR-19) | `:471-523` |
| xref present but `XREF-CUST-ID` points nowhere | = customer missing case | same | business | `:739`, `:711-715` |
| multiple xrefs for one account | first on AIX path (undefined order) | lowest card number (**Q-04**); unobservable on the fixture (50/50) — asserted explicitly | decision | `:727-735` |
| read RESP other than 0/13 | E-12 for the file | 500 E-12 for the file; generic body if outside the read path (B-0031) | technical, surfaced as FR-20 | `:759-768`, `:809-816`, `:858-865` |
| ZIP / phone | 5 of 10 / 13 of 15 shown | full values — **DV-05** | deliberate deviation | `bms:290`, `:318`, `:334` |
| PF3 / PF15 | hard stop XCTL to menu; `CDEMO-USER-TYPE<-'U'`, COMMAREA rewritten | `/menu`; session untouched — **DV-02** | business (FR-21) + deviation | `:324-352` |
| any other AID (PF1, PF5, …) | folded to ENTER -> a search with the current field | **DV-03**: unbound keys do nothing; only `Search`/Enter and `Exit`/`F3`/`Esc` exist | deliberate deviation | `:306-314` |
| `CAVW` started without COMMAREA | entry screen, no auth check | 401 -> `/signon` (session required) | technical | `:282`, `:462-463` |
| `WHEN OTHER` state | `SEND TEXT 'UNEXPECTED DATA SCENARIO'` + abend code `0001` | unreachable (stateless requests); generic 500 if ever hit | technical (demoted) | `:375-382` |
| abend | `ABEND-ROUTINE`, `ABCODE('9999')` | `GlobalExceptionHandler` 500 | technical (demoted, B-0031) | `:264-266`, `:916-937` |
| `RECEIVE MAP` failure | RESP never tested | explicit validation (Q-14) | technical (demoted) | `:611-616` |
| dead 88-levels (`DID-NOT-FIND-*`, `WS-INFORM-OUTPUT`, `WS-EXIT-MESSAGE`, `SEARCHED-ACCT-*`) | never set / never displayed | not ported; documented dead | dead | `:112-135`, `:792`, `:841` |
| `CDEMO-CARD-NUM` captured from xref | never displayed | not carried in the DTO | dead output | `:740` |
| session expiry | n/a | 401 -> `/signon` | technical | B-0027 |
| restart / rerun | n/a (ONLINE) | n/a | — | — |

### Deliberate deviations owned here

| Id | Screens | Legacy (cite) | Target | Required parity assertion |
|---|---|---|---|---|
| **DV-01** | `CACTVWA` | after `ACCTDAT` NOTFND the `SET DID-NOT-FIND-ACCT-IN-ACCTDAT` is commented out (`:792`), so `9000-READ-ACCT` continues (`:704-706`), reads `CUSTDAT` and `1200-SETUP-SCREEN-VARS` shows the customer block and an unread `ACCOUNT-RECORD` area under E-10 (`:471-472`, `:493`) | E-10 only, 404, no account or customer payload; `CustomerRepository` **not called** | `AccountViewServiceTest#accountMasterMissing` asserts 404 + E-10 literal, payload has no blocks, and `verify(customerRepository, never()).findById(...)`; tagged DV-01 / Q-03 |
| **DV-02** | `CACTVWA` / session | `INITIALIZE CARDDEMO-COMMAREA` on entry from the menu (`:283-286`) wipes `CDEMO-USER-ID`; PF3 forces `CDEMO-USRTYP-USER` (`:344`) | identity lives in the HTTP session and is unchanged by entering or leaving Account View | wave-4 integration test: sign on as `USER0001` (`U`) and as the admin fixture (`A`), navigate menu -> Account View -> Exit -> menu; `GET /api/auth/session` returns the same `userId`/`userType` throughout; tagged DV-02 / Q-05 |
| **DV-03** | **`CACTVWA` only** (sign-on and menu halves are in their own program FRs) | every AID other than ENTER/PF3 is remapped to ENTER (`:306-314`), so e.g. PF5 triggers a search | `Search`/`Exit` buttons, Enter submits, `F3`/`Esc` = Exit; unbound keys do nothing (no implicit search) | `account-view.component.spec.ts`: pressing `F5` issues no API call and changes nothing; tagged DV-03 |
| **DV-05** | `CACTVWA` | `ACSZIPC` length 5 of `CUST-ADDR-ZIP X(10)`; `ACSPHN1/2` length 13 of `CUST-PHONE-NUM-1/2 X(15)` (`COACTVW.bms:290-294`, `:318-321`) | `zipCode`, `phone1`, `phone2` carry and display the full stored values | parity test with a fixture customer whose ZIP is 10 chars and phone 15 chars asserts the full values in the DTO and on screen; tagged DV-05 / Q-11 |

### Q-resolutions relevant here (approved, plan §10)

- **Q-02** <11 digits -> E-05; target requires exactly 11 digits, non-zero.
- **Q-03** -> DV-01. **Q-05** -> DV-02. **Q-11** -> DV-05. **Q-06** -> DV-03.
- **Q-04** lowest card number wins.
- **Q-09** literal parts verbatim, reason code appended, no byte parity on RESP digits.
- **Q-10** date type gate decided in wave 1; this program renders the result as 10-char text.
- **Q-14** explicit request validation.

---

## 8. Hard-stop boundary

This program owns the **stream hard stop**: the `XCTL PROGRAM(CDEMO-TO-PROGRAM)` at `:349-352`, which in S-01 always targets `COMEN01C` (`CDEMO-FROM-PROGRAM` is the menu; the fallback literal is `LIT-MENUPGM`, `:335-338`). In the target this is the SPA route to `/menu`. `COACTVWC` is **not responsible for**:

- rendering the menu after the return (MEN-01 / FR-09) — `COMEN01C`;
- how the user got here (option validation and dispatch, FR-10/FR-11 menu half) — `COMEN01C`;
- authentication or session creation (FR-01..FR-08, FR-22) — `COSGN00C`; this program only requires an authenticated session;
- `CAUP` / `COACTUPC` (Account Update) and every other menu option — **excluded**; nothing in this document specifies an update, and the `LIT-CARDDTL*`/`LIT-CCLIST*`/`LIT-CARDUPDATE*` literals (`:151-166`, `:176-182`) are unused here;
- date validation (`CSUTLDTC`, FR-23/24) — not called; dates are displayed as stored;
- the physical schema, repositories, session model and exception handler — delivered by wave 1 and consumed here.

Nothing here specifies past the hard stop.

---

## 9. Acceptance criteria

Backend criteria run `AccountViewService`/`AccountController` on Testcontainers PostgreSQL 16 with the fixture data (`app/data/ASCII/acctdata.txt`, `custdata.txt`, `cardxref.txt`, decoded per the wave-1 importer) and an authenticated test session; UI criteria run in `account-view.component.spec.ts` and the recorded UI pass. Each is traceable to the program requirement (and stream requirement) in column 2.

| Id | Req | Given | When | Then |
|---|---|---|---|---|
| AC-ACV-01 | ACV-01 / FR-13, FR-11(ACV) | authenticated session; menu option 1 selected | `/accounts/view` rendered | account field empty with focus; info line `Enter or update id of account to display`; no account/customer values; no error; header `CAVW`, `COACTVWC`, date/time from the pinned `Clock`; footer `F3=Exit`; **no backend call made** |
| AC-ACV-02 | ACV-02 / FR-14 | `/accounts/view` | Search with `''`, then with `*` | 400 `No input received`; field shows `*` with error styling; no data blocks |
| AC-ACV-03 | ACV-03 / FR-15 | same | Search with `00000000000` | 400 `Account Filter must  be a non-zero 11 digit number` (exactly two spaces after `must`); field keeps `00000000000` with error styling |
| AC-ACV-04 | ACV-03 / FR-15 | same | Search with `1234567890A` and with `1234567890` (10 digits, Q-02) | 400 E-05 for both |
| AC-ACV-05 | V-2/V-3 order | same | Search with `*` | E-04, never E-05 (blank check precedes numeric check) |
| AC-ACV-06 | ACV-04 / FR-16 | 11-digit id with no `card_xrefs` row | `GET /api/accounts/{id}` | 404; message starts with `Account:<id> not found in Cross ref file.  Resp:` and contains ` Reas:`; no data blocks; `AccountRepository`/`CustomerRepository` not called |
| AC-ACV-07 | ACV-05 / FR-17, DV-01 | xref row exists, no `accounts` row | `GET` | 404; message starts `Account:<id> not found in Acct Master file.Resp:`; **no** account or customer block; `CustomerRepository` never called; tagged DV-01 |
| AC-ACV-08 | ACV-06 / FR-18 | xref + account rows exist, no `customers` row for `xref_cust_id` | `GET` | 404; message starts `CustId:<9-digit id> not found in customer master.Resp: ` and contains ` REAS:`; response carries the **account block** (all ten §3.2 account fields) and no customer block; UI shows the account block under the error |
| AC-ACV-09 | ACV-07 / FR-19 | fixture account with xref, account and customer | `GET` | 200 `AccountViewResponse`: every account field of §3.2 equals the fixture record; six money fields formatted `+ZZZ,ZZZ,ZZZ.99` (e.g. `+000,001,000.00`-style sign and grouping exactly as `PICOUT`); dates rendered as the 10-char stored text; `ssn` = `nnn-nn-nnnn` of `CUST-SSN`; all customer fields equal the fixture; `infoMessage` unchanged; no error; account number echoed in the field |
| AC-ACV-10 | DV-05 | fixture customer with 10-char ZIP and 15-char phones | `GET` | `zipCode` length 10, `phone1`/`phone2` length 15, shown in full; tagged DV-05 |
| AC-ACV-11 | V-6 / Q-04 | two `card_xrefs` rows for one account (test-only data) | `GET` | the customer shown is the one referenced by the row with the **lowest** `xref_card_number`; test states the rule explicitly |
| AC-ACV-12 | ACV-08 / FR-20 | `CardXrefRepository` throws (simulated outage) | `GET` | 500; message starts `File Error: READ on CXACAIX  returned RESP ` and contains `,RESP2 `; no stack trace; repeat for `ACCTDAT ` and `CUSTDAT ` by failing the respective repository |
| AC-ACV-13 | ACV-09 / FR-21 | Account View showing data or an error | Exit button / `F3` / `Esc` | SPA at `/menu` in MEN-01 state; returning to `/accounts/view` via option 1 shows AC-ACV-01 (state discarded) |
| AC-ACV-14 | DV-02 | signed on as `USER0001` (`U`); repeat with the admin fixture (`A`) | menu -> Account View -> search -> Exit -> menu | `GET /api/auth/session` returns the same `userId`/`userType` at every step; tagged DV-02 |
| AC-ACV-15 | DV-03 | `/accounts/view` with a value typed | press `F5` | no API call, no change; tagged DV-03 |
| AC-ACV-16 | B-0027 | no session | `GET /api/accounts/00000000001` | 401; SPA routes to `/signon` |
| AC-ACV-17 | B-0031 | an unexpected exception outside the read path | `GET` | 500 with the generic `ErrorResponse`, no stack trace |
| AC-ACV-18 | Q-09 | AC-ACV-06/07/08/12 | inspect messages | literal parts byte-identical to the COBOL `STRING` literals (including the double space in `file.  Resp:` and the trailing space in `master.Resp: `); the reason-code digits are **not** asserted for equality with CICS values |
| AC-ACV-19 | D-0016 | any response | inspect URLs | `CAVW` appears only in the header text; the route is `/accounts/view` and the API `/api/accounts/{acctId}` |

---

## 10. Open questions and assumptions

| Id | Item | Status | Owner / when |
|---|---|---|---|
| A-ACV-1 | Shape of the 404 body for FR-18 (account block returned together with E-11): recommend `AccountViewResponse` with `errorMessage` populated and `customer` null, or `ErrorResponse` plus a partial payload — a wave-4 API design choice; the observable requirement (account block shown under E-11) is fixed | assumption | wave 4 |
| A-ACV-2 | Format of the synthesised reason codes in E-09..E-12 (`Resp:`/`Reas:` slots) — Q-09 fixes that literals stay and digits are not parity-asserted; the concrete code table is a wave-4 choice recorded in `CobolMessages` | assumption | wave 4 |
| A-ACV-3 | Date columns `LocalDate` vs raw text is the **Q-10 gate decided in wave 1** on the full extract; wave 4 consumes the outcome and renders 10-char text either way | dependency on wave 1 | wave 1 |
| A-ACV-4 | Q-02 (<11 digits -> E-05) is INFERRED from the map definition (`PICIN` without `NUM`); approved as the target rule | accepted INFERRED | — |
| A-ACV-5 | `DFHRED`/`DFHBMDAR` attribute constants come from the absent `DFHBMSCA`; interpreted as error styling / dark info line (R-12) | accepted INFERRED | wave 4 UI |
| A-ACV-6 | The `DEFINE AIX` `UNIQUEKEY` answer (R-0001 item 1) may make Q-04 moot; the ordering rule stands either way | open external request | orchestrator |
| — | Undecided boundary | **none** (B-0001, B-0002, B-0007, B-0010, B-0027, B-0028, B-0029, B-0030, B-0031 all DECIDED) | — |

**Divergence from cross-check branches**: not consulted. This document derives from `app/` source on the work branch and the approved stream artifacts only.
