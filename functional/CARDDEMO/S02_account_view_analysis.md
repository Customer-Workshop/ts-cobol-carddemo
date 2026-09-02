# S-02 Account View — Stream Analysis (`!mf_stream_analysis`)

Stream: **S-02 Account View** (ONLINE). Catalog row: `functional/CARDDEMO/CardDemo_inventory.md` §5
(`S-02 | Account View | ONLINE | CAVW | COACTVWC | active`).
Method: same as S-01 (`S01_SignonMenu_analysis.md`) — source-derived, every behavior cited to a
COBOL/BMS/CSD line; nothing invented. Target stack per `CardDemo_target_state.md` (CONFIRMED).

## 1. Scope and surfaces

| Item | Value | Cite |
|---|---|---|
| Transaction | `CAVW` → program `COACTVWC` | `app/csd/CARDDEMO.CSD:186-191` (PROGRAM), `:362-367` (TRANSACTION) |
| Program | `COACTVWC` — "Accept and process ACCOUNT VIEW request" | `app/cbl/COACTVWC.cbl:1-6` |
| Map / mapset | `CACTVWA` / `COACTVW` | `app/bms/COACTVW.bms:1-20`, `COACTVWC.cbl:137-140` |
| Files read | `CXACAIX` (card-xref AIX by account), `ACCTDAT` (account master), `CUSTDAT` (customer master) | `COACTVWC.cbl:81-83`, `:723-870` |
| Files written | none — read-only inquiry | whole program: only `EXEC CICS READ` |
| Callers | main menu option 01 (COMEN01C route registry) via XCTL with `CARDDEMO-COMMAREA`; direct terminal entry also accepted (`EIBCALEN = 0`) | `COMEN01C.cbl:183-186`; `COACTVWC.cbl:282-290` |
| Callees | none in-stream. Return routing: `XCTL PROGRAM(CDEMO-TO-PROGRAM)` (COMEN01C or the calling program) | `COACTVWC.cbl:324-352` |
| Shared copybooks | `COCOM01Y` (COMMAREA), `CVACT01Y` (account), `CVCUS01Y` (customer), `CVACT03Y` (xref), `CSSTRPFY` (AID), `CSMSG01Y`/`CSMSG02Y`, `COTTL01Y`, `CSDAT01Y`, `CSLKPCDY`, `CSUTLDWY` | `COACTVWC.cbl:129-175` |
| Wave | single wave — one program, no internal seams | — |

Single-program stream: the analysis, FR doc, migration plan and program FR doc
(`programs/COACTVWC_functional_requirement.md`) together form the pre-implementation set.

## 2. Screen (BMS map `CACTVWA`)

`app/bms/COACTVW.bms`. 24×80. Header rows 1–2 (TRNNAME/TITLE01/CURDATE, PGMNAME/TITLE02/CURTIME —
`:22-79`), title "View Account" (`:70-72`).

| Screen field | BMS field | Len | Attrs / PIC | Source data | Cite |
|---|---|---|---|---|---|
| Account Number (input) | ACCTSID | 11 | UNPROT, FSET, IC; `PICIN='99999999999'`, `VALIDN=(MUSTFILL)` | echoes `CC-ACCT-ID` or `*` | `bms:84-90`; `cbl:551-565` |
| Active Y/N | ACSTTUS | 1 | ASKIP | ACCT-ACTIVE-STATUS | `bms:97-100`; `cbl:475` |
| Opened | ADTOPEN | 10 | ASKIP | ACCT-OPEN-DATE X(10) | `bms:106-109`; `cbl:481` |
| Credit Limit | ACRDLIM | 15 | `PICOUT='+ZZZ,ZZZ,ZZZ.99'` | ACCT-CREDIT-LIMIT | `bms:115-119`; `cbl:478` |
| Expiry | AEXPDT | 10 | ASKIP | ACCT-EXPIRAION-DATE | `bms:125-128`; `cbl:482` |
| Cash credit Limit | ACSHLIM | 15 | `PICOUT='+ZZZ,ZZZ,ZZZ.99'` | ACCT-CASH-CREDIT-LIMIT | `bms:134-138`; `cbl:479` |
| Reissue | ARISSDT | 10 | ASKIP | ACCT-REISSUE-DATE | `bms:144-147`; `cbl:483` |
| Current Balance | ACURBAL | 15 | `PICOUT='+ZZZ,ZZZ,ZZZ.99'` | ACCT-CURR-BAL | `bms:153-157`; `cbl:477` |
| Current Cycle Credit | ACRCYCR | 15 | `PICOUT='+ZZZ,ZZZ,ZZZ.99'` | ACCT-CURR-CYC-CREDIT | `bms:163-167`; `cbl:485` |
| Account Group | AADDGRP | 10 | ASKIP | ACCT-GROUP-ID | `bms:175-178`; `cbl:487` |
| Current Cycle Debit | ACRCYDB | 15 | `PICOUT='+ZZZ,ZZZ,ZZZ.99'` | ACCT-CURR-CYC-DEBIT | `bms:184-188`; `cbl:486` |
| Customer id | ACSTNUM | 9 | ASKIP | CUST-ID 9(09) | `bms:203-206`; `cbl:495` |
| SSN | ACSTSSN | 12 | ASKIP | CUST-SSN formatted `nnn-nn-nnnn` | `bms:212-215`; `cbl:496-503` |
| Date of birth | ACSTDOB | 10 | ASKIP | CUST-DOB-YYYY-MM-DD | `bms:221-224`; `cbl:505` |
| FICO Score | ACSTFCO | 3 | ASKIP | CUST-FICO-CREDIT-SCORE 9(03) | `bms:232-236`; `cbl:504` |
| First / Middle / Last Name | ACSFNAM / ACSMNAM / ACSLNAM | 25 / 25 / 25 | ASKIP | CUST-FIRST/MIDDLE/LAST-NAME | `bms:241-263`; `cbl:506-508` |
| Address line 1 / 2 | ACSADL1 / ACSADL2 | 50 / 50 | ASKIP | CUST-ADDR-LINE-1/2 | `bms:264-283`; `cbl:509-510` |
| State | ACSSTTE | 2 | ASKIP | CUST-ADDR-STATE-CD | `bms:285-288`; `cbl:512` |
| City | ACSCITY | 50 | ASKIP | CUST-ADDR-LINE-3 | `bms:294-297`; `cbl:511` |
| Zip | ACSZIPC | 5 | ASKIP, JUSTIFY=RIGHT | CUST-ADDR-ZIP X(10) → truncated to 5 | `bms:299-302`; `cbl:514` |
| Country | ACSCTRY | 3 | ASKIP | CUST-ADDR-COUNTRY-CD | `bms:304-307`; `cbl:513` |
| Phone 1 / Phone 2 | ACSPHN1 / ACSPHN2 | 13 / 13 | ASKIP | CUST-PHONE-NUM-1/2 X(15) → truncated to 13 | `bms:313-326`; `cbl:515-516` |
| Government Issued Id | ACSGOVT | 20 | ASKIP | CUST-GOVT-ISSUED-ID | `bms:333-336`; `cbl:517` |
| EFT Account Id | ACSEFTC | 10 | ASKIP | CUST-EFT-ACCOUNT-ID | `bms:342-345`; `cbl:518` |
| Primary Card Holder Y/N | ACSPFLG | 1 | ASKIP | CUST-PRI-CARD-HOLDER-IND | `bms:351-354`; `cbl:519` |
| Info message | INFOMSG | 45 | NEUTRAL, ASKIP | WS-INFO-MSG | `bms:356-361`; `cbl:527-531` |
| Error message | ERRMSG | 78 | RED, BRT, ASKIP | WS-RETURN-MSG X(75) | `bms:362-368`; `cbl:533` |
| Footer | — | — | `F3=Exit` | — | `bms:369-373` |

Note: only ENTER and F3 are listed on the screen; the program treats any other AID as ENTER (§4).

## 3. Control flow (`COACTVWC.cbl:261-407`)

1. **Entry** (`:261-290`): initialize working storage; if `EIBCALEN = 0` (direct terminal entry) or
   entered from the menu program (`CDEMO-FROM-PROGRAM = 'COMEN01C'` and not re-enter) the COMMAREA
   is initialised (`:282-290`). Otherwise the incoming COMMAREA is copied into `CARDDEMO-COMMAREA`
   and the program-private `THIS-PROGCOMMAREA` (`:291-297`).
2. **AID mapping** (`:301-314`): `EIBAID` → `CCARD-AID-*` via `CSSTRPFY`; ENTER and PF3 are the only
   valid keys (`:306-309`); anything else is forced to ENTER (`:311-314`).
3. **PF3** (`:324-352`): sets `CDEMO-TO-PROGRAM` to the caller (`CDEMO-FROM-PROGRAM`) or, if that is
   blank/low-values, to the menu (`COMEN01C`), sets `CDEMO-USRTYP-USER`, `CDEMO-PGM-ENTER`, and
   `XCTL`s (**B-012** return routing).
4. **First display** (`:353-360`): `CDEMO-PGM-ENTER` (fresh entry) → screen sent with an empty account
   field and the prompt info message, no data lookup.
5. **Re-entry with ENTER** (`:362-379`): `2000-PROCESS-INPUTS` (receive + edit); on `INPUT-ERROR` the
   screen is re-sent with the error; otherwise `9000-READ-ACCT`, then the screen is sent.
6. **Common return** (`:388-407`): `WS-RETURN-MSG` copied into `CCARD-ERROR-MSG`; pseudo-conversational
   `RETURN TRANSID('CAVW')` with the combined COMMAREA.

## 4. Input edit (`2210-EDIT-ACCOUNT`, `:648-685`) — validation order

Receive (`:596-642`): `ACCTSIDI = '*' OR SPACES` → `CC-ACCT-ID = LOW-VALUES`; else the 11 raw
characters. Then, first failure wins:

| # | Condition | Effect | Message | Cite |
|---|---|---|---|---|
| 1 | account blank (low-values/spaces, i.e. empty or `*`) | INPUT-ERROR, FLG-ACCTFILTER-BLANK, CDEMO-ACCT-ID=0; then `2200` unconditionally SETs NO-SEARCH-CRITERIA-RECEIVED | **`No input received`** (overrides the intermediate `Account number not provided`) | `:653-661`, `:628-633` |
| 2 | not 11 numeric digits, or all zeroes | INPUT-ERROR, FLG-ACCTFILTER-NOT-OK, CDEMO-ACCT-ID=0 | **`Account Filter must  be a non-zero 11 digit number`** (two spaces after `must`, literal at `:672`) | `:666-676` |
| 3 | otherwise | FLG-ACCTFILTER-ISVALID, CDEMO-ACCT-ID = input | — | `:678-681` |

The 88-level `Account number must be a non zero 11 digit number` (`:104-105`) is declared but never
SET — dead text, not a message. `WS-INFORM-OUTPUT` (`Displaying details of given Account`, `:111-112`)
is likewise never SET: the info line always shows the prompt (`:528-530`).

The terminal-side `VALIDN=(MUSTFILL)` and `PICIN='99999999999'` (`bms:87-88`) mean a partially typed
field never reaches the program; the target enforces the same as an 11-character maximum plus the
edit above (a short entry fails edit #2).

## 5. Data access (`9000-READ-ACCT`, `:687-870`) — order and outcomes

```
9200 READ CXACAIX  KEY = account id (11)          → XREF-CUST-ID, XREF-CARD-NUM into COMMAREA
     NOTFND → FLG-ACCTFILTER-NOT-OK, msg A  → exit (no account / customer data)
     OTHER  → FLG-ACCTFILTER-NOT-OK, msg F(CXACAIX) → exit
9300 READ ACCTDAT  KEY = account id (11)          → ACCOUNT-RECORD
     NOTFND → FLG-ACCTFILTER-NOT-OK, msg B  → exit
     OTHER  → FLG-ACCTFILTER-NOT-OK, msg F(ACCTDAT) → exit
9400 READ CUSTDAT  KEY = XREF-CUST-ID (9)         → CUSTOMER-RECORD
     NOTFND → FLG-CUSTFILTER-NOT-OK, msg C   (account data STILL displayed, `:471-491`)
     OTHER  → FLG-CUSTFILTER-NOT-OK, msg F(CUSTDAT)
```

Messages are built with `STRING ... DELIMITED BY SIZE INTO WS-RETURN-MSG` (X(75)); the receiver is
filled left to right and the overflow is dropped. `ERROR-RESP`/`ERROR-RESP2` are `PIC X(10)`
receiving `WS-RESP-CD`/`WS-REAS-CD` (`S9(09) COMP`, `:36-37`, `:56-59`): a numeric-to-alphanumeric
MOVE yields the 9 unsigned digits left-justified plus one trailing space. For NOTFND, RESP=13 and
RESP2=80 (CICS File Control constants), so the visible texts are deterministic:

| Msg | Exact 75-char text (`<id>` = the 11/9-digit key) | Cite |
|---|---|---|
| A | `Account:<id> not found in Cross ref file.  Resp:000000013  Reas:0000` | `:741-758` |
| B | `Account:<id> not found in Acct Master file.Resp:000000013  Reas:0000` | `:789-807` |
| C | `CustId:<id> not found in customer master.Resp: 000000013  REAS:0000000` | `:839-857` |
| F | `File Error: READ     on <file>   returned RESP <resp>,RESP2 <resp2>` (`<file>` = `CXACAIX  `/`ACCTDAT  `/`CUSTDAT  `, X(9)) | `:759-769`, `:809-819`, `:858-868` |

`CXACAIX` is the alternate index on the card-xref file keyed by account id: the keyed READ returns
the **first** xref record for that account (lowest card number in base-key order). The shared
`ICardXrefRepository.GetFirstByAccountIdAsync` (orders by card number) is the exact target seam.

## 6. Output shaping (`1200-SETUP-SCREEN-VARS`, `:452-533`)

- Account field echo: blank input → low-values, then `*` in red on re-entry (`:461-464`, `:561-565`);
  any other input echoed as typed (`:465-466`); red when `FLG-ACCTFILTER-NOT-OK` (`:557-559`).
- Account block filled when `FOUND-ACCT-IN-MASTER OR FOUND-CUST-IN-MASTER` (`:471-491`); customer
  block only when `FOUND-CUST-IN-MASTER` (`:494-523`). Unfilled blocks stay low-values (blank).
- Amounts: `S9(10)V99` moved to `+ZZZ,ZZZ,ZZZ.99` (15): fixed sign, zero suppression (commas in the
  suppressed region become spaces), zero → `+           .00`, integer digits above 9 are dropped.
- SSN: `nnn-nn-nnnn` via STRING of substrings (`:496-503`). FICO `9(03)` → 3 digits (leading zeros
  kept). Customer id → 9 digits. Zip → first 5 of X(10); phones → first 13 of X(15) (alphanumeric MOVE
  truncation into the shorter map fields).
- Info message: always `Enter or update id of account to display` (`:107-108`, `:528-530`).
- Header: `CAVW`, `COACTVWC`, `COTTL01Y` titles, `mm/dd/yy`, `hh:mm:ss` (`:432-448`) — demoted.

## 7. Data dictionary (fields read by S-02)

| Copybook / column (shared schema, commit 468e17d) | PIC | Target type | Used for |
|---|---|---|---|
| `CVACT01Y` ACCT-ID → `accounts.acct_id` | 9(11) | string(11) key | lookup, echo |
| ACCT-ACTIVE-STATUS → `acct_active_status` | X(01) | string | ACSTTUS |
| ACCT-CURR-BAL / CREDIT-LIMIT / CASH-CREDIT-LIMIT / CURR-CYC-CREDIT / CURR-CYC-DEBIT | S9(10)V99 | decimal | 5 amount fields |
| ACCT-OPEN-DATE / EXPIRAION-DATE / REISSUE-DATE | X(10) | DateOnly? | 3 date fields |
| ACCT-GROUP-ID | X(10) | string | AADDGRP |
| `CVACT03Y` XREF-ACCT-ID / XREF-CUST-ID / XREF-CARD-NUM → `card_xref` | 9(11)/9(09)/X(16) | strings | AIX lookup → customer key |
| `CVCUS01Y` CUST-ID → `customers.cust_id` | 9(09) | string(9) key | lookup, ACSTNUM |
| CUST-*-NAME, ADDR-LINE-1..3, STATE-CD, COUNTRY-CD, ZIP, PHONE-NUM-1/2, GOVT-ISSUED-ID, EFT-ACCOUNT-ID, PRI-CARD-HOLDER-IND | X(n) | string | customer block |
| CUST-SSN | 9(09) | string(9) | ACSTSSN |
| CUST-DOB-YYYY-MM-DD | X(10) | DateOnly? | ACSTDOB |
| CUST-FICO-CREDIT-SCORE | 9(03) | short | ACSTFCO |

No field or index missing from the shared data layer: **no EF migration for S-02**.

## 8. Boundaries

| Id | Class | Description | Decision for S-02 |
|---|---|---|---|
| B-009 | Persistence | VSAM KSDS/AIX → PostgreSQL | REUSE shared `IAccountRepository`, `ICustomerRepository`, `ICardXrefRepository`; NOTFND ↔ `null`; other RESP ↔ store exception |
| B-012 | Dynamic routing | `XCTL PROGRAM(CDEMO-TO-PROGRAM)` on PF3 (`:350`) | Exit navigates to the caller route; the only registry caller is the main menu (`/menu`). COMMAREA `CDEMO-USRTYP-USER` reset on exit (`:344`) is demoted: unreachable for admins (no admin-menu option), no observable effect. |
| S01-B6 | Session | COMMAREA → JWT `SessionContext` | REUSE; route behind `authGuard` (program has no user-type check, so no `adminGuard`) |
| S02-B1 (new, stream-local) | AID parity | COACTVWC treats every AID other than ENTER/PF3 **as ENTER** (`:311-314`) — differs from the S-01 shared "invalid key" convention | Source wins: F3 = Exit, any other F-key submits. `classifyAidKey` reused for key detection; the `invalid` branch maps to submit for this screen. |
| S02-B2 (new, stream-local) | Message | Msg F embeds CICS RESP/RESP2 that have no target equivalent | Same template; RESP/RESP2 rendered as `000000017 ` / `000000120 ` (DFHRESP IOERR / VSAM I/O error) for any store failure. Documented deviation, technical path only. |

## 9. Risks

- Message texts contain doubled spaces and CICS codes; tests assert exact strings (FR doc §3).
- Amount edit picture drops the 10th integer digit; the sample data never exceeds 9 digits but the
  formatter reproduces the truncation to keep parity for any value.
- Header date/time and APPLID/SYSID are not part of any FR (demoted).

## 10. Validation of this analysis

`cobc -I app/cpy -fsign=EBCDIC -x app/cbl/COACTVWC.cbl` is a CICS program (EXEC CICS) and cannot be
compiled stand-alone; the analysis relies on line-by-line reading of `COACTVWC.cbl` (941 lines),
`COACTVW.bms` (378 lines), the copybooks in §1 and the CSD entries.
