# S-01 AccountView — stream functional requirements (`!mf_stream_fr_generation`)

| Item | Value |
|---|---|
| Module | CardDemo core (`functional/CardDemo/CardDemo_inventory.md`) |
| Stream | **S-01 AccountView** — confirmed at STOP B (`.migration/06_decisions.md` D-0019) |
| Process type | **ONLINE** (CICS pseudo-conversational) |
| Input analysis | `functional/CardDemo/AccountView_analysis.md` (pinned scope, inventory, surfaces, field dictionary, boundary table) |
| Entry | trancode `CAVW` -> `COACTVWC`, map `CACTVWA` / mapset `COACTVW` (`app/csd/CARDDEMO.CSD:317-318`, `:181-185`) — reached from sign-on `CC00` -> `COSGN00C` (`CSD:378-379`) and main menu `CM00` -> `COMEN01C` (`CSD:399-400`) option 1 |
| Hard stop | `EXEC CICS XCTL PROGRAM(CDEMO-TO-PROGRAM)` back to `COMEN01C` on PF3 (`app/cbl/COACTVWC.cbl:349-352`) |
| Exclusions | `CAUP`/`COACTUPC` and every other menu option (2..11); `COADM01C` admin branch of sign-on. The menu shell renders, only option 1 is live. |
| Programs in scope | `COSGN00C`, `COMEN01C` (shared, owned by S-01), `COACTVWC` (stream-private), `CSUTLDTC` (shared utility, owned by S-01 per D-0020, no in-stream caller) |
| Output language | English. Message text is in-source (`WS-RETURN-MSG`/`WS-MESSAGE` literals, `CSMSG01Y`, BMS `INITIAL=`) and is quoted **verbatim including double spaces**; where text is assembled at run time from CICS `RESP`/`RESP2` values this is stated. |
| Evidence policy | Source-derived only (no mainframe, D-0011: source is ASCII, no `iconv`). Every claim cites `<file>:<line>`. FACT = read from source; INFERRED = depends on CICS/BMS/compiler behaviour not in the repo. |
| Branch | `devin/1788757216-cardemo-account-view-stream` |

Rubric applied (playbook): a branch is a **functional requirement** only if it has a business trigger stateable in domain terms **and** an observable result. Routing, COMMAREA/state-machine mechanics, formatting internals, technical failures and dead code are **demoted** to §7/§11 with cites.

---

## 1. Purpose and scope

**What the stream does for the business.** A regular (non-admin) user signs on with a user id and password, reaches the main menu, chooses *Account View*, enters an 11-digit account number and sees the account's status, limits, balances, cycle amounts, key dates and group, together with the details of the customer who holds the card cross-referenced to that account. The user then returns to the menu with F3. The stream reads four data stores and writes none (`COACTVWC.cbl:727`, `:776`, `:826`; `COSGN00C.cbl:211` — the only `EXEC CICS READ`s; no `WRITE`/`REWRITE`/`DELETE` in any of the four programs, `AccountView_analysis.md` §5.2).

**Process type**: ONLINE, three screens (`COSGN0A`, `COMEN1A`, `CACTVWA`) driven by ENTER/PF keys.

**Trigger chain (FACT)**: terminal `CC00` -> `COSGN00C` (`CSD:378-379`) -> on valid regular user `XCTL 'COMEN01C'` (`COSGN00C.cbl:236-239`) -> option `1` -> `XCTL 'COACTVWC'` via the option table (`COMEN01C.cbl:184-187`; `app/cpy/COMEN02Y.cpy:25-29`) -> `CAVW` pseudo-conversation (`COACTVWC.cbl:402-406`) -> PF3 -> `XCTL COMEN01C` (**hard stop**, `COACTVWC.cbl:324-352`).

**In scope**: every path of `COACTVWC`; the regular-user path of `COSGN00C`; `COMEN01C` render, option validation, option-1 dispatch, PF3 exit; the callable contract of `CSUTLDTC`.

**Excluded (documented, not specified)**: `COSGN00C.cbl:230-234` admin XCTL to `COADM01C`; `COMEN01C.cbl:147-176` option 11 / `DUMMY` routes; `COMEN01C.cbl:177-188` for options 2..10 (`COACTUPC`, `COCRDLIC`, `COCRDSLC`, `COCRDUPC`, `COTRN00C`, `COTRN01C`, `COTRN02C`, `CORPT00C`, `COBIL00C`, `COMEN02Y.cpy:31-84`). Nothing about `COACTUPC` is specified here.

---

## 2. Actors and preconditions

| Actor / precondition | Detail | Cite |
|---|---|---|
| Regular user | A person holding a `USRSEC` record with `SEC-USR-TYPE = 'U'` | `app/cpy/CSUSR01Y.cpy:17-23`; `app/cpy/COCOM01Y.cpy:26-28` |
| Admin user | `SEC-USR-TYPE = 'A'` — leaves the stream at sign-on (excluded) | `COSGN00C.cbl:230-234` |
| Terminal session | 3270 session starting transaction `CC00`; no state before sign-on (`EIBCALEN = 0`) | `COSGN00C.cbl:80-83` |
| User security store | `USRSEC` VSAM KSDS keyed by 8-char user id (B-0006) | `CSD:88-89`; `COSGN00C.cbl:211-219` |
| Card cross-reference | `CXACAIX` alternate-index path over `CCXREF`, keyed by account id (B-0007) | `CSD:63-65`; `COACTVWC.cbl:727-735` |
| Account master | `ACCTDAT` keyed by `ACCT-ID 9(11)` (B-0001) | `CSD:1-2`; `app/cpy/CVACT01Y.cpy:5` |
| Customer master | `CUSTDAT` keyed by `CUST-ID 9(09)` (B-0002) | `CSD:50-52`; `app/cpy/CVCUS01Y.cpy:5` |
| Identity carried between screens | COMMAREA `CDEMO-USER-ID`, `CDEMO-USER-TYPE`, `CDEMO-PGM-CONTEXT` (B-0027) | `COCOM01Y.cpy:19-44` |
| Date utility caller | none in S-01; `CSUTLDTC` is invoked by later streams (`COTRN02C`, `CORPT00C`, `COACTUPC`) | `AccountView_analysis.md` §2 row 4; D-0020 |

---

## 3. Surface specification (ONLINE)

BMS attribute meanings: `UNPROT` = user-enterable; fields without `UNPROT` are display-only; `IC` = initial cursor; `DRK` = non-display; `FSET` = always transmitted.

### 3.1 Sign-on screen `COSGN0A` (`app/bms/COSGN00.bms`, `app/cpy-bms/COSGN00.CPY`)

| Field | I/O | Length / PIC | Label (verbatim) | Mandatory | Edit rules | Initial value | Cite |
|---|---|---|---|---|---|---|---|
| `USERID` | INPUT | 8, `USERIDI PIC X(8)` | `'User ID     :'`; hint `'(8 Char)'` | yes | blank/low-values -> E-01; upper-cased before use | empty, cursor here | `COSGN00.bms:155`, `:169`; `COSGN00.CPY:72`; `COSGN00C.cbl:82`, `:118-122`, `:132-134` |
| `PASSWD` | INPUT | 8, `PASSWDI PIC X(8)`, `DRK` | `'Password    :'`; hint `'(8 Char)'` | yes | blank/low-values -> E-02; upper-cased; byte-equal compare with `SEC-USR-PWD` | `'________'` | `COSGN00.bms:174`, `:180`, `:189`; `COSGN00.CPY:78`; `COSGN00C.cbl:123-127`, `:135-136`, `:223` |
| `TRNNAME`, `PGMNAME` | DISPLAY | 4 / 8 | `'Tran :'`, `'Prog :'` | — | `'CC00'`, `'COSGN00C'` | — | `COSGN00.bms:33`, `:56`; `COSGN00C.cbl:183-184` |
| `CURDATE`, `CURTIME` | DISPLAY | 8 / 8 | `'Date :'`, `'Time :'` | — | `mm/dd/yy`, `hh:mm:ss` from server clock | placeholders `'mm/dd/yy'`, `'Ahh:mm:ss'` | `COSGN00.bms:46-51`, `:69-74`; `COSGN00C.cbl:179-196`; `app/cpy/CSDAT01Y.cpy:30-41` |
| `APPLID`, `SYSID` | DISPLAY | 8 / 8 | `'AppID:'`, `'SysID:'` | — | CICS `ASSIGN APPLID/SYSID` | — | `COSGN00.bms:79`, `:88`; `COSGN00C.cbl:198-204` |
| `TITLE01`, `TITLE02` | DISPLAY | 40 / 40 | — | — | `'      AWS Mainframe Modernization       '`, `'              CardDemo                  '` | — | `app/cpy/COTTL01Y.cpy:18-22`; `COSGN00C.cbl:181-182` |
| constant text | DISPLAY | — | banner rows incl. `'Type your User ID and Password, then press ENTER'` (continued literal), footer `'ENTER=Sign-on  F3=Exit'` | — | — | — | `COSGN00.bms:98-149`, `:205` |
| `ERRMSG` | DISPLAY | 80 (`WS-MESSAGE X(80)`) | — | — | messages of §5 | spaces | `COSGN00C.cbl:38`, `:77-78`, `:149` |

AID keys: ENTER = sign-on, PF3 = exit, any other = E-03 (`COSGN00C.cbl:85-95`).

### 3.2 Main menu screen `COMEN1A` (`app/bms/COMEN01.bms`, `app/cpy-bms/COMEN01.CPY`)

| Field | I/O | Length / PIC | Label (verbatim) | Edit rules | Cite |
|---|---|---|---|---|---|
| `OPTION` | INPUT | 2, `OPTIONI PIC X(2)` | `'Please select an option :'` | trailing blanks trimmed, value right-justified into 2 chars with blank -> `'0'`; must be numeric, `> 0`, `<= 11`; in S-01 only `1` is live | `COMEN01.bms:144`; `COMEN01.CPY:132`; `COMEN01C.cbl:117-134`; `COMEN02Y.cpy:21` |
| `OPTN001..OPTN012` | DISPLAY | 40 each | built as `nn. <name>`, e.g. `'01. Account View'` | 11 rows filled, row 12 blank | `COMEN01C.cbl:262-303`; `COMEN02Y.cpy:25-90`, `:94` |
| header | DISPLAY | — | `'Tran:'`, `'Date:'`, `'Prog:'`, `'Time:'`, `'Main Menu'`; `TRNNAME='CM00'`, `PGMNAME='COMEN01C'` | — | `COMEN01.bms:33-79`; `COMEN01C.cbl:240-257` |
| footer | DISPLAY | — | `'ENTER=Continue  F3=Exit'` | — | `COMEN01.bms:162` |
| `ERRMSG` | DISPLAY | 80 | — | messages of §5 | `COMEN01C.cbl:79-80`, `:213` |

AID keys: ENTER = select option, PF3 = back to sign-on, other = E-03 (`COMEN01C.cbl:93-103`).

### 3.3 Account View screen `CACTVWA` (`app/bms/COACTVW.bms`, `app/cpy-bms/COACTVW.CPY`)

**Input field (the only one)**

| Field | Length / PIC | Label (verbatim) | Mandatory | Edit rules | Initial | Cite |
|---|---|---|---|---|---|---|
| `ACCTSID` | 11, `ACCTSIDI PIC 99999999999`, BMS `PICIN='99999999999'`, attributes `FSET,IC,NORM,UNPROT` (no `NUM`) | `'Account Number :'` | yes (on ENTER) | `'*'` or spaces -> treated as not supplied (E-04); not numeric or all zeroes -> E-05; otherwise accepted and echoed | empty, cursor here | `COACTVW.bms:83-89`; `COACTVW.CPY:55-60`; `COACTVWC.cbl:628-633`, `:649-681`, `:543-552` |

**Display-only fields** (populated `COACTVWC.cbl:431-534`; labels `COACTVW.bms`)

| Screen field | Length / `PICOUT` | Label (verbatim) | Source field | Cite |
|---|---|---|---|---|
| `ACSTTUS` | 1 | `'Active Y/N: '` | `ACCT-ACTIVE-STATUS X(1)` | `bms:96`; `cbl:473`; `CVACT01Y.cpy:6` |
| `ADTOPEN` | 10 | `'Opened:'` | `ACCT-OPEN-DATE X(10)` | `bms:106`; `cbl:487` |
| `ACRDLIM` | 15, `+ZZZ,ZZZ,ZZZ.99` | `'Credit Limit        :'` | `ACCT-CREDIT-LIMIT S9(10)V99` | `bms:116-120`; `cbl:477` |
| `AEXPDT` | 10 | `'Expiry:'` | `ACCT-EXPIRAION-DATE X(10)` | `bms:127`; `cbl:488` |
| `ACSHLIM` | 15, `+ZZZ,ZZZ,ZZZ.99` | `'Cash credit Limit   :'` | `ACCT-CASH-CREDIT-LIMIT` | `bms:137-141`; `cbl:479-480` |
| `AREISDT` | 10 | `'Reissue:'` | `ACCT-REISSUE-DATE X(10)` | `bms:148`; `cbl:489` |
| `ACURBAL` | 15, `+ZZZ,ZZZ,ZZZ.99` | `'Current Balance     :'` | `ACCT-CURR-BAL` | `bms:158-162`; `cbl:475` |
| `ACRCYCR` | 15, `+ZZZ,ZZZ,ZZZ.99` | `'Current Cycle Credit:'` | `ACCT-CURR-CYC-CREDIT` | `bms:170`; `cbl:482-483` |
| `AADDGRP` | 10 | `'Account Group:'` | `ACCT-GROUP-ID X(10)` | `bms:181`; `cbl:490` |
| `ACRCYDB` | 15, `+ZZZ,ZZZ,ZZZ.99` | `'Current Cycle Debit :'` | `ACCT-CURR-CYC-DEBIT` | `bms:191`; `cbl:485` |
| `ACSTNUM` | 9 | `'Customer id  :'` | `CUST-ID 9(09)` | `bms:206`; `cbl:494` |
| `ACSTSSN` | 12 | `'SSN:'` | `CUST-SSN 9(09)` rendered `nnn-nn-nnnn` | `bms:215`; `cbl:496-504` |
| `ACSTDOB` | 10 | `'Date of birth:'` | `CUST-DOB-YYYY-MM-DD X(10)` | `bms:224`; `cbl:507` |
| `ACSTFCO` | 3 | `'FICO Score:'` | `CUST-FICO-CREDIT-SCORE 9(03)` | `bms:233`; `cbl:505-506` |
| `ACSFNAM` / `ACSMNAM` / `ACSLNAM` | 25 each | `'First Name'`, `'Middle Name: '`, `'Last Name : '` | `CUST-FIRST/MIDDLE/LAST-NAME X(25)` | `bms:242-250`; `cbl:508-510` |
| `ACSADL1`, `ACSADL2` | 50 each | `'Address:'` | `CUST-ADDR-LINE-1/2 X(50)` | `bms:267`; `cbl:511-512` |
| `ACSSTTE` | 2 | `'State '` | `CUST-ADDR-STATE-CD X(02)` | `bms:276`; `cbl:514` |
| `ACSZIPC` | 5 | `'Zip'` | `CUST-ADDR-ZIP X(10)` (truncated to 5 on the map) | `bms:290`; `cbl:515` |
| `ACSCITY` | 50 | `'City '` | `CUST-ADDR-LINE-3 X(50)` | `bms:300`; `cbl:513` |
| `ACSCTRY` | 3 | `'Country'` | `CUST-ADDR-COUNTRY-CD X(03)` | `bms:309`; `cbl:516` |
| `ACSPHN1`, `ACSPHN2` | 13 each | `'Phone 1:'`, `'Phone 2:'` | `CUST-PHONE-NUM-1/2 X(15)` (truncated to 13) | `bms:318`, `:334`; `cbl:517-518` |
| `ACSGOVT` | 20 | `'Government Issued Id Ref    : '` | `CUST-GOVT-ISSUED-ID X(20)` | `bms:325`; `cbl:519` |
| `ACSEFTC` | 10 | `'EFT Account Id: '` | `CUST-EFT-ACCOUNT-ID X(10)` | `bms:341`; `cbl:520` |
| `ACSPFLG` | 1 | `'Primary Card Holder Y/N:'` | `CUST-PRI-CARD-HOLDER-IND X(01)` | `bms:350`; `cbl:521-522` |
| `INFOMSG` | 45 | — | `WS-INFO-MSG` (always `'Enter or update id of account to display'`, see §7.4) | `COACTVW.CPY:234`; `cbl:113-116`, `:528-534` |
| `ERRMSG` | 78 (`WS-RETURN-MSG X(75)`) | — | messages of §5 | `cbl:117`, `:532` |
| header / footer | — | `'Tran:'`, `'Date:'`, `'Prog:'`, `'Time:'`, `'View Account'`, `'Customer Details'`, `'  F3=Exit '`; `TRNNAME='CAVW'`, `PGMNAME='COACTVWC'` | — | `bms:33-78`, `:202`, `:373`; `cbl:436-453` |

AID keys: ENTER = search; PF3 = exit to menu; **every other key is treated as ENTER** (`COACTVWC.cbl:306-314`; `app/cpy/CSSTRPFY.cpy:21-78` maps PF13..PF24 onto PF1..PF12, so PF15 = PF3).

### 3.4 `CSUTLDTC` callable contract (surface `none`, no screen)

`PROCEDURE DIVISION USING LS-DATE PIC X(10), LS-DATE-FORMAT PIC X(10), LS-RESULT PIC X(80)` (`app/cbl/CSUTLDTC.cbl:84-88`). `RETURN-CODE` = severity (`:98`). Result layout (`CSUTLDTC.cbl:42-57`, identical to `app/cpy/CSUTLDWY.cpy:60-85`):

| Offset | Len | Content |
|---|---|---|
| 1 | 4 | `WS-SEVERITY` `9(4)` zero-padded |
| 5 | 11 | `'Mesg Code:'` + 1 pad space |
| 16 | 4 | `WS-MSG-NO` `9(4)` |
| 20 | 1 | space |
| 21 | 15 | `WS-RESULT` text (see §5.4) |
| 36 | 1 | space |
| 37 | 9 | `'TstDate:'` + 1 pad space |
| 46 | 10 | `WS-DATE` (see §11 Q-07 for the length-prefix defect) |
| 56 | 1 | space |
| 57 | 10 | `'Mask used:'` |
| 67 | 10 | `WS-DATE-FMT` = the mask passed in |
| 77 | 4 | spaces |

---

## 4. Functional requirements (KEEP items only)

Owning programs: SGN = `COSGN00C`, MEN = `COMEN01C`, ACV = `COACTVWC`, DTC = `CSUTLDTC`. Covering-test names follow the CORE conventions (`<Program>ServiceTest#<method>`, `ApiIntegrationTest#...`, Angular `*.spec.ts`); `VC-nn` are verification cases for later parity/UI testing. The CICS trancode `CAVW` is recorded here for traceability only and must not appear in any URL (D-0016).

| ID | Flow | Business trigger | Observable result | Owner | Source cite | Boundary | Covering test |
|---|---|---|---|---|---|---|---|
| FR-01 | Sign-on screen | User opens the application (no prior state) | Sign-on screen with empty User ID (cursor there) and masked Password, header `CC00`/`COSGN00C`, current date `mm/dd/yy`, time `hh:mm:ss`, application id and system id, footer `'ENTER=Sign-on  F3=Exit'`; no message | SGN | `COSGN00C.cbl:80-83`, `:145-157`, `:177-204`; `COSGN00.bms:155-205` | B-0030 | `SignOnServiceTest#initialScreenHasNoMessage`; `sign-on.component.spec.ts` |
| FR-02 | Sign-on edit | User presses ENTER with User ID blank | E-01 `'Please enter User ID ...'` shown, cursor on User ID, no lookup performed | SGN | `COSGN00C.cbl:117-122`, `:138-140` | — | `SignOnServiceTest#blankUserIdRejected` |
| FR-03 | Sign-on edit | User ID present, Password blank | E-02 `'Please enter Password ...'`, cursor on Password, no lookup | SGN | `COSGN00C.cbl:123-127`, `:138-140` | — | `SignOnServiceTest#blankPasswordRejected` |
| FR-04 | Sign-on | User enters id/password in any letter case | Both are upper-cased before lookup and comparison, so `admin`/`ADMIN` are the same user and password | SGN | `COSGN00C.cbl:132-136` | B-0026 | `SignOnServiceTest#credentialsAreCaseInsensitive` |
| FR-05 | Sign-on success | User id exists, password matches, user type `U` | Main menu displayed (FR-09); the session now carries user id and type `U` | SGN -> MEN | `COSGN00C.cbl:221-229`, `:235-239`; `COMEN01C.cbl:85-90` | B-0006, B-0009, B-0027 | `SignOnServiceTest#regularUserReachesMenu`; `ApiIntegrationTest#loginThenMenu` |
| FR-06 | Sign-on failure | User id exists, password does not match | E-06 `'Wrong Password. Try again ...'`, cursor on Password, user stays on sign-on | SGN | `COSGN00C.cbl:241-246` | B-0006 | `SignOnServiceTest#wrongPassword` |
| FR-07 | Sign-on failure | User id does not exist | E-07 `'User not found. Try again ...'`, cursor on User ID | SGN | `COSGN00C.cbl:247-251` | B-0006 | `SignOnServiceTest#unknownUser` |
| FR-08 | Sign-on exit | User presses F3 on the sign-on screen | Plain-text screen `'Thank you for using CardDemo application...'` and the session ends (no further transaction) | SGN | `COSGN00C.cbl:88-90`, `:162-172`; `app/cpy/CSMSG01Y.cpy:18-19` | — | `SignOnServiceTest#f3EndsSession`; `sign-on.component.spec.ts` |
| FR-09 | Menu display | User arrives at the menu (after sign-on or after F3 from Account View) | Menu with 11 numbered options `'01. Account View'` .. `'11. Pending Authorization View'`, header `CM00`/`COMEN01C`, footer `'ENTER=Continue  F3=Exit'`, option field empty, no message | MEN | `COMEN01C.cbl:85-90`, `:208-220`, `:262-303`; `COMEN02Y.cpy:21-90` | B-0033 | `MenuServiceTest#rendersElevenOptions`; `menu.component.spec.ts` |
| FR-10 | Menu edit | User presses ENTER with option blank, `0`, non-numeric, or greater than 11 | E-08 `'Please enter a valid option number...'`; menu re-shown; the option field echoes the normalised 2-digit value (blank -> `00`) | MEN | `COMEN01C.cbl:117-134`; `COMEN02Y.cpy:21` | — | `MenuServiceTest#invalidOptionRejected` |
| FR-11 | Menu option 1 | User enters `1` (also ` 1`, `01`, `1 `) and presses ENTER | Account View screen displayed in its entry state (FR-13) | MEN -> ACV | `COMEN01C.cbl:117-125`, `:177-187`; `COMEN02Y.cpy:25-29`; `COACTVWC.cbl:282-286`, `:353-360` | B-0010(a), B-0027 | `MenuServiceTest#optionOneRoutesToAccountView`; `ApiIntegrationTest#menuToAccountView` |
| FR-12 | Menu exit | User presses F3 on the menu | Sign-on screen shown fresh (FR-01); session identity dropped | MEN -> SGN | `COMEN01C.cbl:96-98`, `:196-203`; `COSGN00C.cbl:80-83` | B-0010(c) | `MenuServiceTest#f3ReturnsToSignOn` |
| FR-13 | Account View entry | User arrives from menu option 1 | Screen with empty Account Number (cursor there), info line `'Enter or update id of account to display'`, no account or customer data, no error | ACV | `COACTVWC.cbl:282-286`, `:353-360`, `:460-469`, `:528-534`, `:543-552` | — | `AccountViewServiceTest#entryStatePromptOnly`; `account-view.component.spec.ts` |
| FR-14 | Account edit | User presses ENTER (or any key other than F3) with Account Number empty or `*` | E-04 `'No input received'` in red; the account field shows `*` in red; no data shown | ACV | `COACTVWC.cbl:628-633`, `:653-662`, `:640-642`, `:561-565` | — | `AccountViewServiceTest#blankAccountRejected` |
| FR-15 | Account edit | Account Number contains a non-digit or is all zeroes (INFERRED: also fewer than 11 digits, see Q-02) | E-05 `'Account Filter must  be a non-zero 11 digit number'`; field turned red, entered value echoed; no data shown | ACV | `COACTVWC.cbl:666-676`, `:557-559` | — | `AccountViewServiceTest#nonNumericOrZeroAccountRejected` |
| FR-16 | Lookup — no card for account | Valid 11-digit number with no card cross-reference record | E-09 `'Account:<id> not found in Cross ref file.  Resp:<r> Reas:<r2>'`; field red; no account or customer data | ACV | `COACTVWC.cbl:691-699`, `:737-758` | **B-0007** | `AccountViewServiceTest#accountWithoutCardXrefNotFound` |
| FR-17 | Lookup — account master missing | Cross-reference exists but no account master record | E-10 `'Account:<id> not found in Acct Master file.Resp:<r> Reas:<r2>'`; **legacy quirk**: the customer lookup still runs and, when the customer exists, the customer block is displayed under the error (Q-03) | ACV | `COACTVWC.cbl:701-715`, `:786-807`, `:471-472`, `:493` | **B-0001**, B-0002 | `AccountViewServiceTest#accountMasterMissing` |
| FR-18 | Lookup — customer missing | Cross-reference and account exist, customer master record does not | E-11 `'CustId:<id> not found in customer master.Resp: <r> REAS:<r2>'`; account block displayed, customer block blank | ACV | `COACTVWC.cbl:708-715`, `:836-857`, `:471-491`, `:493` | **B-0002** | `AccountViewServiceTest#customerMissingShowsAccountOnly` |
| FR-19 | Lookup — success | Cross-reference, account and customer all exist | Account block (status, opened/expiry/reissue dates, credit limit, cash credit limit, current balance, cycle credit, cycle debit, group) and customer block (id, SSN `nnn-nn-nnnn`, DOB, FICO, names, address lines, state, zip, city, country, phones, government id, EFT account, primary-holder flag) shown; money as `+ZZZ,ZZZ,ZZZ.99`; account number echoed; no error; info line unchanged | ACV | `COACTVWC.cbl:737-740`, `:787-788`, `:837-838`, `:471-534`; `COACTVW.bms:120`, `:141`, `:162` | B-0007, B-0001, B-0002 | `AccountViewServiceTest#fullDetailsReturned`; `ApiIntegrationTest#accountViewHappyPath` |
| FR-20 | Lookup — data store failure | Any of the three reads fails for a reason other than not-found | E-12 `'File Error: READ on <file> returned RESP <r>,RESP2 <r2>'`; field red; no data | ACV | `COACTVWC.cbl:759-768`, `:809-818`, `:858-867`, `:86-105` | B-0007, B-0001, B-0002 | `AccountViewServiceTest#repositoryFailureReported` |
| FR-21 | Account View exit (hard stop) | User presses F3 (or F15) on Account View | Main menu displayed (FR-09); the account search state is discarded | ACV -> MEN | `COACTVWC.cbl:324-352`; `CSSTRPFY.cpy:34-35`, `:58-59` | B-0010(b), B-0027 | `AccountViewServiceTest#f3ReturnsToMenu`; `account-view.component.spec.ts` |
| FR-22 | Sign-on data store failure | The user security store cannot be read (not a not-found) | E-13 `'Unable to verify the User ...'`, cursor on User ID | SGN | `COSGN00C.cbl:252-256` | B-0006 | `SignOnServiceTest#userStoreFailure` |
| FR-23 | Date validation — valid | A caller passes a date and a mask that parse as a real calendar date | Result `'0000Mesg Code: 0000 Date is valid   TstDate:<date> Mask used:<mask>   '` and return code `0` | DTC | `CSUTLDTC.cbl:88-100`, `:105-130` | B-0014, B-0015 | `DateValidationServiceTest#validDateReturnsSeverityZero` |
| FR-24 | Date validation — invalid | The date does not parse for the mask | Severity `0003` with a message number and 15-char reason text per §5.4 (e.g. `2513 Unsupp. Range`, `2517 Invalid month`, `2520 Nonnumeric data`); any other feedback -> `'Date is invalid'`; return code = severity | DTC | `CSUTLDTC.cbl:60-70`, `:122-149` | B-0014, **B-0015** | `DateValidationServiceTest#invalidDateReasons` |
| FR-25 | Invalid key | User presses a key other than ENTER/F3 on sign-on or menu | E-03 `'Invalid key pressed. Please see below...'`; screen re-shown unchanged | SGN, MEN | `COSGN00C.cbl:91-94`; `COMEN01C.cbl:99-102`; `CSMSG01Y.cpy:20-21` | — | `SignOnServiceTest#invalidKey`; `MenuServiceTest#invalidKey` |

**Counts**: 25 requirements; 8 boundary-derived (FR-05, FR-06, FR-07, FR-16, FR-17, FR-18, FR-20, FR-22; plus FR-23/FR-24 which specify the shared-utility contract B-0014/B-0015).

**Requirements with no obvious covering test in the target**: FR-08 (session end via plain text — target has no `SEND TEXT`; covered only by UI spec), FR-25 (PF-key handling has no keyboard equivalent in a browser form; target must decide whether it exists at all — Q-06). Both are marked `UI-only / decision` in §9.

---

## 5. Validation and error catalogue

All texts are source literals unless marked *dynamic*. "Blocking" = the user cannot proceed until corrected; none of the messages is a warning.

| Code | Program | Trigger | Text (verbatim) | Text source | Blocking | Resulting state | Cite |
|---|---|---|---|---|---|---|---|
| E-01 | SGN | User ID blank | `Please enter User ID ...` | literal | yes | sign-on re-shown, cursor User ID | `COSGN00C.cbl:118-122` |
| E-02 | SGN | Password blank | `Please enter Password ...` | literal | yes | sign-on re-shown, cursor Password | `:123-127` |
| E-03 | SGN, MEN | AID other than ENTER/PF3 | `Invalid key pressed. Please see below...` (padded to 50) | `CSMSG01Y.cpy:20-21` | yes | same screen re-shown | `COSGN00C.cbl:91-94`; `COMEN01C.cbl:99-102` |
| E-04 | ACV | account field empty or `*` | `No input received` (overwrites `Account number not provided`, see §6.3) | literal | yes | field `*` red, no data | `COACTVWC.cbl:121-124`, `:653-662`, `:640-642`, `:561-565` |
| E-05 | ACV | account not numeric or zero | `Account Filter must  be a non-zero 11 digit number` (two spaces after `must`) | literal | yes | field red, value echoed | `:666-676` |
| E-06 | SGN | password mismatch | `Wrong Password. Try again ...` | literal | yes | cursor Password | `:241-246` |
| E-07 | SGN | user id not in store (RESP 13 NOTFND) | `User not found. Try again ...` | literal | yes | cursor User ID | `:247-251` |
| E-08 | MEN | option blank/0/non-numeric/>11 | `Please enter a valid option number...` | literal | yes | menu re-shown | `COMEN01C.cbl:127-134` |
| E-09 | ACV | no xref for account | `Account:` + 11-digit id + ` not found in Cross ref file.  Resp:` + RESP(10) + ` Reas:` + RESP2 — *dynamic*, truncated to 75 chars | STRING literals | yes | field red, no data | `COACTVWC.cbl:744-757` |
| E-10 | ACV | account not in ACCTDAT | `Account:` + id + ` not found in Acct Master file.Resp:` + RESP(10) + ` Reas:` + RESP2 — *dynamic* | STRING literals | yes | field red; customer block may still show (Q-03) | `:793-806` |
| E-11 | ACV | customer not in CUSTDAT | `CustId:` + 9-digit id + ` not found in customer master.Resp: ` + RESP(10) + ` REAS:` + RESP2 — *dynamic* | STRING literals | yes | account block shown, customer blank | `:845-856` |
| E-12 | ACV | READ RESP other than 0/13 | `File Error: READ on <CXACAIX |ACCTDAT |CUSTDAT > returned RESP <r>,RESP2 <r2>` — *dynamic* | `WS-FILE-ERROR-MESSAGE` layout | yes | field red, no data | `:86-105`, `:759-766`, `:809-816`, `:858-865` |
| E-13 | SGN | USRSEC READ RESP other than 0/13 | `Unable to verify the User ...` | literal | yes | cursor User ID | `:252-256` |
| E-14 | SGN | PF3 | `Thank you for using CardDemo application...` (padded to 50) — informational exit text | `CSMSG01Y.cpy:18-19` | n/a | session ended | `COSGN00C.cbl:88-90`, `:162-172` |

**RESP/RESP2 rendering caveat (INFERRED)**: `WS-RESP-CD`/`WS-REAS-CD` are `S9(9) COMP` moved to `PIC X(10)` (`COACTVWC.cbl:40-43`, `:98`, `:102`, `:745-746`); the printed digits (leading zeros, sign) depend on compiler move rules and cannot be verified off-host. Target messages should reproduce the literal parts and carry the reason as a code, not attempt byte parity (analysis R-06).

### 5.4 `CSUTLDTC` result catalogue (FACT from the feedback-token constants)

Each 88-level constant encodes `SEVERITY` (bytes 1-2) and `MSG-NO` (bytes 3-4) as binary (`CSUTLDTC.cbl:61-73`); the program copies them into the result as 4-digit numbers (`:123-124`) and picks the 15-char text (`:128-149`).

| Feedback constant | Severity | Msg no | `WS-RESULT` text (15 chars) |
|---|---|---|---|
| `FC-INVALID-DATE` = X'00000000...' (success token despite its name) | `0000` | `0000` | `Date is valid  ` |
| `FC-INSUFFICIENT-DATA` X'000309CB' | `0003` | `2507` | `Insufficient   ` |
| `FC-BAD-DATE-VALUE` X'000309CC' | `0003` | `2508` | `Datevalue error` |
| `FC-INVALID-ERA` X'000309CD' | `0003` | `2509` | `Invalid Era    ` |
| `FC-UNSUPP-RANGE` X'000309D1' | `0003` | `2513` | `Unsupp. Range  ` |
| `FC-INVALID-MONTH` X'000309D5' | `0003` | `2517` | `Invalid month  ` |
| `FC-BAD-PIC-STRING` X'000309D6' | `0003` | `2518` | `Bad Pic String ` |
| `FC-NON-NUMERIC-DATA` X'000309D8' | `0003` | `2520` | `Nonnumeric data` |
| `FC-YEAR-IN-ERA-ZERO` X'000309D9' | `0003` | `2521` | `YearInEra is 0 ` |
| any other token | as returned | as returned | `Date is invalid` |

Which input produces which CEEDAYS token is **not in the repo** (B-0015 UNRESOLVED); callers in later streams branch on `'0000'` and `'2513'` (`app/cbl/COTRN02C.cbl:398-401`, per analysis B-0014).

---

## 6. Field and data derivations

### 6.1 Sign-on
- `WS-USER-ID` / `CDEMO-USER-ID` = `UPPER-CASE(USERIDI)`; `WS-USER-PWD` = `UPPER-CASE(PASSWDI)` (`COSGN00C.cbl:132-136`). Lookup key = `WS-USER-ID X(08)` (`:45`, `:215`). Match = `SEC-USR-PWD = WS-USER-PWD` byte compare of two 8-char fields (`:223`; `CSUSR01Y.cpy:21`).
- Session identity = `CDEMO-USER-ID`, `CDEMO-USER-TYPE <- SEC-USR-TYPE`, `CDEMO-PGM-CONTEXT = 0`, `CDEMO-FROM-TRANID='CC00'`, `CDEMO-FROM-PROGRAM='COSGN00C'` (`:224-228`).
- Header date `mm/dd/yy` = month, day, last two digits of year from `FUNCTION CURRENT-DATE`; time `hh:mm:ss` (`:179-196`; `CSDAT01Y.cpy:17-41`).

### 6.2 Menu
- Option normalisation: scan `OPTIONI` from the right for the last non-blank, take `OPTIONI(1:idx)` right-justified into `WS-OPTION-X X(02) JUST RIGHT`, replace blanks by `'0'`, move to `WS-OPTION 9(02)`; echoed to `OPTIONO` (`COMEN01C.cbl:117-125`). Hence `'1 '` -> `'1'` -> `' 1'` -> `'01'` -> 1; `'  '` -> `'00'` -> 0 (E-08); `'A1'` -> not numeric (E-08).
- Route = `CDEMO-MENU-OPT-PGMNAME(WS-OPTION)`; option 1 = `'COACTVWC'` (`COMEN02Y.cpy:28`, `:93-98`).
- Option labels = `CDEMO-MENU-OPT-NUM` (`9(02)`, so `01`) + `'. '` + `CDEMO-MENU-OPT-NAME X(35)` (`COMEN01C.cbl:269-272`).

### 6.3 Account View input
- `CC-ACCT-ID X(11)` = `ACCTSIDI`, or LOW-VALUES when `ACCTSIDI = '*'` or spaces (`COACTVWC.cbl:628-633`; `CVCRD01Y.cpy:34`). The `'*'` is what the program itself writes back into the field after a blank submit (`:561-565`), so re-submitting an untouched screen is again "not supplied".
- Blank path sets `WS-RETURN-MSG` to `'Account number not provided'` (`:657-659`) and then, in the cross-field edit, to `'No input received'` (`:640-642`) — the second `SET` overwrites the first, so **only `'No input received'` is ever displayed** (FACT).
- Valid path: `CDEMO-ACCT-ID <- CC-ACCT-ID`, `WS-CARD-RID-ACCT-ID <- CDEMO-ACCT-ID` (`:678`, `:691`).

### 6.4 Account View lookups
- Xref read by alternate key `WS-CARD-RID-ACCT-ID-X` (11 chars) on `CXACAIX`; on success `CDEMO-CUST-ID <- XREF-CUST-ID`, `CDEMO-CARD-NUM <- XREF-CARD-NUM` (`:727-740`; `CVACT03Y.cpy:5-7`). If the account has several cards, the first record on the path is used (B-0007 UNRESOLVED, Q-04).
- Account read by the same 11-char key on `ACCTDAT` into `ACCOUNT-RECORD` (`:776-784`; `CVACT01Y.cpy:5-17`).
- Customer read by `WS-CARD-RID-CUST-ID-X` (9 chars) `<- CDEMO-CUST-ID` on `CUSTDAT` into `CUSTOMER-RECORD` (`:708`, `:826-834`; `CVCUS01Y.cpy:5-23`).

### 6.5 Account View output
- Account block shown when `FOUND-ACCT-IN-MASTER OR FOUND-CUST-IN-MASTER` (`:471-472`); customer block when `FOUND-CUST-IN-MASTER` (`:493`).
- Money fields: `S9(10)V99` moved to map fields with `PICOUT='+ZZZ,ZZZ,ZZZ.99'` — explicit sign, thousands separators, two decimals (`COACTVW.bms:120`, `:141`, `:162`, `:173`, `:194`; `cbl:475-485`).
- SSN: `CUST-SSN(1:3) '-' CUST-SSN(4:2) '-' CUST-SSN(6:4)` (`:496-504`).
- Zip: first 5 of `X(10)`; phones: first 13 of `X(15)` (map lengths `COACTVW.bms:290-294`, `:318-321`, `:334-337`).
- Dates (`ACCT-OPEN-DATE`, `ACCT-EXPIRAION-DATE`, `ACCT-REISSUE-DATE`, `CUST-DOB-YYYY-MM-DD`) are moved as 10-char text without validation (`:487-489`, `:507`) — format INFERRED `YYYY-MM-DD` (analysis §4.2 R-02).
- Info line: `WS-INFO-MSG` is reset in `9000-READ-ACCT` (`:689`) and then defaulted to the prompt whenever blank (`:528-530`); the alternative text `'Displaying details of given Account'` (`:115-116`) is never set anywhere, so the info line is constant.
- Account field colour: default; red when `FLG-ACCTFILTER-NOT-OK`, or when blank on re-entry (`:555-565`; `DFHRED` meaning INFERRED, R-12).

### 6.6 `CSUTLDTC`
- Input `LS-DATE`/`LS-DATE-FORMAT` are wrapped as 10-byte VSTRINGs for CEEDAYS (`CSUTLDTC.cbl:105-113`); severity/message from the 12-byte feedback token (`:123-124`); reason text per §5.4; `RETURN-CODE <- WS-SEVERITY-N` (`:98`).

---

## 7. Mechanics (demoted, cited)

### 7.1 Pseudo-conversation and COMMAREA (B-0027, B-0028)
- Each program ends with `RETURN TRANSID(own tranid) COMMAREA(...)` (`COSGN00C.cbl:98-102`; `COMEN01C.cbl:107-110`; `COACTVWC.cbl:394-406`). First entry is `EIBCALEN = 0` or `CDEMO-PGM-CONTEXT = 0`; re-entry is `1` (`COCOM01Y.cpy:29-31`; `COMEN01C.cbl:87-92`; `COACTVWC.cbl:353-374`, `:581`).
- `COACTVWC` **re-initialises the whole COMMAREA** when `EIBCALEN = 0` or when `CDEMO-FROM-PROGRAM = 'COMEN01C' AND NOT CDEMO-PGM-REENTER` (`:282-286`). The condition tests the working-storage copy of the COMMAREA *before* `DFHCOMMAREA` is copied into it (`:288-289`), so whether it fires on a fresh task depends on residual storage (INFERRED); when it does, `CDEMO-USER-ID` is blank for the rest of the Account View conversation and on the PF3 hand-off. Independently of that, `CDEMO-USER-TYPE` is unconditionally forced to `'U'` at `:344` (FACT). Target keeps identity in the server session (analysis R-08; decision at plan stop).
- `COACTVWC` appends `WS-THIS-PROGCOMMAREA` (`CA-FROM-PROGRAM`, `CA-FROM-TRANID`) to the COMMAREA (`:213-218`, `:288-292`, `:397-400`); the fields are never read for a decision.

### 7.2 Routing (B-0009, B-0010)
- Sign-on: `A` -> `COADM01C` (excluded), else `COMEN01C` (`COSGN00C.cbl:230-240`).
- Menu: `WHEN OTHER` XCTL to the option-table program (`COMEN01C.cbl:177-187`); option 11 guarded by `INQUIRE PROGRAM` with `'This option <name> is not installed...'` fallback (`:147-168`, B-0032, excluded); `DUMMY*` placeholder `'This option <name>is coming soon ...'` with no table entry today (`:169-176`, dead); PF3 exit XCTL `COSGN00C` **without** COMMAREA so sign-on starts with `EIBCALEN = 0` (`:196-203`).
- Hard stop: `CDEMO-TO-PROGRAM <- CDEMO-FROM-PROGRAM` if set, else `'COMEN01C'`; because of §7.1 it is always `'COMEN01C'` (`COACTVWC.cbl:328-339`, `:168-169`); stamps `FROM-TRANID='CAVW'`, `FROM-PROGRAM='COACTVWC'`, `LAST-MAP/MAPSET` (`:341-347`).

### 7.3 AID handling
- `CSSTRPFY` maps `EIBAID` to `CCARD-AID-*`, folding PF13..24 onto PF1..12 (`CSSTRPFY.cpy:21-78`); `COACTVWC` accepts only ENTER/PF3 and rewrites any other AID to ENTER (`:306-314`). `RESP` on every `RECEIVE MAP` is captured and never tested (`COSGN00C.cbl:113`; `COMEN01C.cbl:231`; `COACTVWC.cbl:614`; R-09).

### 7.4 Dead or unreachable code (FACT — document, do not port)
- `COACTVWC`: `DID-NOT-FIND-ACCT-IN-ACCTDAT` / `DID-NOT-FIND-CUST-IN-CUSTDAT` are 88-levels on `WS-RETURN-MSG` (`:131-134`) whose `SET`s are commented out (`:792`, `:842`) while the NOTFND branches store different text, so the tests at `:704` and `:713` are always false — this is the cause of the FR-17 quirk. `WS-INFORM-OUTPUT`, `WS-EXIT-MESSAGE`, `SEARCHED-ACCT-ZEROES`, `SEARCHED-ACCT-NOT-NUMERIC`, `XREF-READ-ERROR`, `CODING-TO-BE-DONE` are declared (`:115-138`) and never set. `SEND-LONG-TEXT` is never performed (`:767-768`, `:817-818`, `:866-867`, `:896-909`). Duplicate paragraph `0000-MAIN-EXIT` (`:408-413`). `WHEN OTHER` `'UNEXPECTED DATA SCENARIO'` requires `CDEMO-PGM-CONTEXT` outside {0,1} (`:375-382`). `COPY CVACT02Y` is unused (`:248`). Literals for card-list/card-update/card-detail navigation (`:151-183`) are never referenced.
- `COMEN01C`: admin-only guard `'No access - Admin Only option... '` (`:136-143`) is unreachable because every table entry is `'U'` (`COMEN02Y.cpy:29-90`); it also indexes the table with `WS-OPTION` before the range check has stopped processing (R-07). Commented-out user-id moves at `:181-182`.
- `CSUTLDTC`: the `DISPLAY` and `GOBACK` alternatives are commented (`:96`, `:101`).

### 7.5 Technical failures (demoted)
- Abend protocol: `HANDLE ABEND` -> `ABEND-ROUTINE` sends `ABEND-DATA` (`'UNEXPECTED ABEND OCCURRED.'` default) and abends with `'9999'` (`COACTVWC.cbl:264-266`, `:916-937`; `app/cpy/CSMSG02Y.cpy:21-29`; B-0031). Target: generic 500 handler (target state).
- Header plumbing: `ASSIGN APPLID/SYSID`, `CURRENT-DATE` (B-0030); BMS attribute bytes `DFHBMFSE`, `DFHDFCOL`, `DFHRED`, `DFHBMDAR`, `DFHNEUTR` from the absent IBM copybook (`COACTVWC.cbl:543-571`; R-12).

---

## 8. Acceptance criteria (Given / When / Then)

| ID | Given | When | Then |
|---|---|---|---|
| FR-01 | no session | the application is opened | sign-on screen: empty User ID with focus, masked Password, date/time/app/system ids, footer `ENTER=Sign-on  F3=Exit`, no message |
| FR-02 | sign-on screen | ENTER with User ID blank | `Please enter User ID ...`; focus User ID; user store not consulted |
| FR-03 | User ID filled | ENTER with Password blank | `Please enter Password ...`; focus Password; user store not consulted |
| FR-04 | user `USER0001`/`PASSWORD` exists | `user0001`/`password` entered | sign-on succeeds exactly as with upper case |
| FR-05 | user exists, type `U`, correct password | ENTER | main menu shown; session holds user id and type `U` |
| FR-06 | user exists | wrong password | `Wrong Password. Try again ...`; still on sign-on; focus Password |
| FR-07 | user id unknown | ENTER | `User not found. Try again ...`; focus User ID |
| FR-08 | sign-on screen | F3 | `Thank you for using CardDemo application...`; session ended |
| FR-09 | signed on as `U` | menu shown | 11 options `01. Account View` .. `11. Pending Authorization View`, footer `ENTER=Continue  F3=Exit`, option empty |
| FR-10 | menu | ENTER with ``, `0`, `12`, `A` | `Please enter a valid option number...`; menu re-shown with normalised option (`00`, `00`, `12`, `0A`) |
| FR-11 | menu | `1`, ` 1`, `01` or `1 ` + ENTER | Account View entry screen |
| FR-12 | menu | F3 | fresh sign-on screen; identity dropped |
| FR-13 | option 1 chosen | Account View shown | empty account field with focus, info `Enter or update id of account to display`, no data, no error |
| FR-14 | Account View | ENTER with field empty (or `*`) | red `No input received`; field shows `*` in red; no data |
| FR-15 | Account View | `00000000000`, `ABC`, `1234567890A` + ENTER | red `Account Filter must  be a non-zero 11 digit number`; value echoed; no data |
| FR-16 | account id with no card xref | ENTER | `Account:<id> not found in Cross ref file.  Resp:.. Reas:..`; no data |
| FR-17 | xref exists, account master missing | ENTER | `Account:<id> not found in Acct Master file.Resp:.. Reas:..`; legacy shows customer block if customer exists (decision Q-03) |
| FR-18 | xref + account exist, customer missing | ENTER | `CustId:<id> not found in customer master.Resp: .. REAS:..`; account block shown; customer block blank |
| FR-19 | all three records exist | ENTER | all account and customer fields shown with `+ZZZ,ZZZ,ZZZ.99` money and `nnn-nn-nnnn` SSN; no error; info line unchanged |
| FR-20 | a read fails technically | ENTER | `File Error: READ on <file> returned RESP ..,RESP2 ..`; no data |
| FR-21 | Account View (any state) | F3 or F15 | main menu shown; search state gone |
| FR-22 | user store unavailable | ENTER on sign-on | `Unable to verify the User ...`; focus User ID |
| FR-23 | `20240229`, mask `YYYYMMDD` | validate | `0000Mesg Code: 0000 Date is valid   TstDate:... Mask used:YYYYMMDD    `, return code 0 |
| FR-24 | `20240230`, mask `YYYYMMDD` (or non-numeric / bad mask) | validate | severity `0003`, message number and text from §5.4, return code 3 |
| FR-25 | sign-on or menu | key other than ENTER/F3 | `Invalid key pressed. Please see below...`; screen unchanged |

---

## 9. Traceability matrix

| Req | Owning program | Source cite | Boundary | Covering test | Verification case |
|---|---|---|---|---|---|
| FR-01 | COSGN00C | `COSGN00C.cbl:80-83,145-157,177-204` | B-0030 | `SignOnServiceTest#initialScreenHasNoMessage`; `sign-on.component.spec.ts` | VC-01 |
| FR-02 | COSGN00C | `:117-122` | — | `SignOnServiceTest#blankUserIdRejected` | VC-02 |
| FR-03 | COSGN00C | `:123-127` | — | `SignOnServiceTest#blankPasswordRejected` | VC-03 |
| FR-04 | COSGN00C | `:132-136` | B-0026 | `SignOnServiceTest#credentialsAreCaseInsensitive` | VC-04 |
| FR-05 | COSGN00C, COMEN01C | `:221-239`; `COMEN01C.cbl:85-90` | B-0006, B-0009, B-0027 | `SignOnServiceTest#regularUserReachesMenu`; `ApiIntegrationTest#loginThenMenu` | VC-05 |
| FR-06 | COSGN00C | `:241-246` | B-0006 | `SignOnServiceTest#wrongPassword` | VC-06 |
| FR-07 | COSGN00C | `:247-251` | B-0006 | `SignOnServiceTest#unknownUser` | VC-07 |
| FR-08 | COSGN00C | `:88-90,162-172` | — | **UI-only**: `sign-on.component.spec.ts` | VC-08 |
| FR-09 | COMEN01C | `COMEN01C.cbl:85-90,208-220,262-303` | B-0033 | `MenuServiceTest#rendersElevenOptions`; `menu.component.spec.ts` | VC-09 |
| FR-10 | COMEN01C | `:117-134` | — | `MenuServiceTest#invalidOptionRejected` | VC-10 |
| FR-11 | COMEN01C, COACTVWC | `:117-125,177-187`; `COACTVWC.cbl:282-286,353-360` | B-0010(a), B-0027 | `MenuServiceTest#optionOneRoutesToAccountView`; `ApiIntegrationTest#menuToAccountView` | VC-11 |
| FR-12 | COMEN01C | `:96-98,196-203` | B-0010(c) | `MenuServiceTest#f3ReturnsToSignOn` | VC-12 |
| FR-13 | COACTVWC | `COACTVWC.cbl:282-286,353-360,460-469,528-534,543-552` | — | `AccountViewServiceTest#entryStatePromptOnly`; `account-view.component.spec.ts` | VC-13 |
| FR-14 | COACTVWC | `:628-633,640-642,653-662,561-565` | — | `AccountViewServiceTest#blankAccountRejected` | VC-14 |
| FR-15 | COACTVWC | `:666-676,557-559` | — | `AccountViewServiceTest#nonNumericOrZeroAccountRejected` | VC-15 |
| FR-16 | COACTVWC | `:691-699,737-758` | B-0007 | `AccountViewServiceTest#accountWithoutCardXrefNotFound` | VC-16 |
| FR-17 | COACTVWC | `:701-715,786-807,471-472,493` | B-0001, B-0002 | `AccountViewServiceTest#accountMasterMissing` | VC-17 |
| FR-18 | COACTVWC | `:708-715,836-857,471-493` | B-0002 | `AccountViewServiceTest#customerMissingShowsAccountOnly` | VC-18 |
| FR-19 | COACTVWC | `:737-740,787-788,837-838,471-534` | B-0007, B-0001, B-0002 | `AccountViewServiceTest#fullDetailsReturned`; `ApiIntegrationTest#accountViewHappyPath` | VC-19 |
| FR-20 | COACTVWC | `:86-105,759-768,809-818,858-867` | B-0007, B-0001, B-0002 | `AccountViewServiceTest#repositoryFailureReported` | VC-20 |
| FR-21 | COACTVWC | `:324-352` | B-0010(b), B-0027 | `AccountViewServiceTest#f3ReturnsToMenu`; `account-view.component.spec.ts` | VC-21 |
| FR-22 | COSGN00C | `COSGN00C.cbl:252-256` | B-0006 | `SignOnServiceTest#userStoreFailure` | VC-22 |
| FR-23 | CSUTLDTC | `CSUTLDTC.cbl:88-100,105-130` | B-0014, B-0015 | `DateValidationServiceTest#validDateReturnsSeverityZero` | VC-23 |
| FR-24 | CSUTLDTC | `:60-70,122-149` | B-0014, B-0015 | `DateValidationServiceTest#invalidDateReasons` | VC-24 |
| FR-25 | COSGN00C, COMEN01C | `COSGN00C.cbl:91-94`; `COMEN01C.cbl:99-102` | — | **UI-only / decision Q-06**: `SignOnServiceTest#invalidKey`; `MenuServiceTest#invalidKey` | VC-25 |

Legacy trancodes for parity records: `CC00` (FR-01..08, 22, 25), `CM00` (FR-09..12, 25), `CAVW` (FR-13..21). They are recorded here only; target routes are business names (`/accounts/view`, D-0016).

---

## 10. Program index (input to `!mf_program_fr_generation`)

| Program | Source | Role | Requirements | Error codes | Demoted items | Program FR |
|---|---|---|---|---|---|---|
| `COSGN00C` | `app/cbl/COSGN00C.cbl` | sign-on screen, credential check, routing to menu (shared, owned by S-01) | FR-01..FR-08, FR-22, FR-25 | E-01, E-02, E-03, E-06, E-07, E-13, E-14 | §7.1 RETURN CC00; §7.2 admin XCTL (excluded); §7.5 header plumbing | [`programs/COSGN00C_functional_requirement.md`](programs/COSGN00C_functional_requirement.md) |
| `COMEN01C` | `app/cbl/COMEN01C.cbl` | main-menu screen, option validation, option-1 dispatch, exit to sign-on (shared, owned by S-01) | FR-09..FR-12, FR-25 | E-03, E-08 | §7.2 option 11 / DUMMY / options 2..10 (excluded); §7.4 admin guard (unreachable) | [`programs/COMEN01C_functional_requirement.md`](programs/COMEN01C_functional_requirement.md) |
| `COACTVWC` | `app/cbl/COACTVWC.cbl` | Account View screen, account-id edits, xref -> account -> customer reads, display, PF3 hard stop (stream-private) | FR-11 (target), FR-13..FR-21 | E-04, E-05, E-09, E-10, E-11, E-12 | §7.1 COMMAREA wipe; §7.3 AID folding; §7.4 dead code; §7.5 abend | [`programs/COACTVWC_functional_requirement.md`](programs/COACTVWC_functional_requirement.md) |
| `CSUTLDTC` | `app/cbl/CSUTLDTC.cbl` | date-validation subroutine (shared, owned by S-01, D-0020; no S-01 caller) | FR-23, FR-24 | §5.4 catalogue | §7.4 commented DISPLAY/GOBACK; B-0015 CEEDAYS | [`programs/CSUTLDTC_functional_requirement.md`](programs/CSUTLDTC_functional_requirement.md) |

Contract-only copybooks used: `COCOM01Y`, `COMEN02Y`, `CVACT01Y`, `CVCUS01Y`, `CVACT03Y`, `CSUSR01Y`, `CVCRD01Y`, `CSSTRPFY`, `CSDAT01Y`, `CSMSG01Y`, `CSMSG02Y`, `COTTL01Y`, `CSUTLDWY` (layout twin of the `CSUTLDTC` result), symbolic maps `COSGN00.CPY`, `COMEN01.CPY`, `COACTVW.CPY`.

---

## 11. Open questions and assumptions

| Id | Question / assumption | Evidence | Needed from |
|---|---|---|---|
| Q-01 | **Admin users**: sign-on routes `A` users to `COADM01C`, which is excluded. What does the target show an admin who signs on during S-01 (unsupported message, or hidden)? | `COSGN00C.cbl:230-234`; R-11 | plan stop (B-0010 facade decision) |
| Q-02 | **Short account numbers (INFERRED)**: `ACCTSID` has `PICIN='99999999999'` but no `NUM` attribute; an entry shorter than 11 digits is received left-justified with pad characters and fails `IS NOT NUMERIC`, producing E-05. Confirm the target treats <11 digits as E-05 (message text says "11 digit"). | `COACTVW.bms:84-89`; `COACTVWC.cbl:666-667` | BA confirmation |
| Q-03 | **Account-missing quirk (FACT)**: after ACCTDAT NOTFND the program still reads CUSTDAT and displays the customer block (and the account block from an unread `ACCOUNT-RECORD` area) beneath E-10. Reproduce or treat as defect (show error only)? Recommendation: defect — show error only. | `COACTVWC.cbl:704-715`, `:792`, `:471-472`; R-05 | STOP C decision |
| Q-04 | **Multiple cards per account**: xref is read via the AIX path and the *first* record wins; uniqueness of the AIX is not in the repo. Which card/customer should the target pick? | `COACTVWC.cbl:727-735`; B-0007 UNRESOLVED | plan stop (blocker) |
| Q-05 | **Lost user id**: `COACTVWC` may wipe the COMMAREA on entry (condition evaluated on an as-yet-unloaded working-storage copy — INFERRED whether it fires) and unconditionally forces user type `U` on PF3 (FACT), so the menu can be re-entered with a blank user id. Target proposal: keep identity in the server session (deviation, not parity). | `COACTVWC.cbl:282-292`, `:344`; R-08 | STOP C decision (B-0027) |
| Q-06 | **PF-key semantics in a browser**: FR-25 (invalid key) and "any key acts as ENTER" (`COACTVWC.cbl:312-314`) have no natural web equivalent. Proposal: buttons `Sign on`/`Exit`, `Continue`/`Exit`, `Search`/`Exit`; FR-25 becomes untestable and is dropped from the target unless the BA wants a keyboard shortcut map. | `COSGN00C.cbl:91-94`; `COMEN01C.cbl:99-102` | BA decision |
| Q-07 | **`CSUTLDTC` result defect (FACT)**: line 122 moves the whole `WS-DATE-TO-TEST` group (2-byte binary length + text) into `WS-DATE X(10)`, overwriting the correct value set at line 108, so the `TstDate:` segment of the result carries two length bytes and only the first 8 date characters. Port the intended value (the date as passed) or the byte-exact behaviour? Recommendation: intended value; callers never parse this segment. | `CSUTLDTC.cbl:107-108`, `:122` | STOP C decision |
| Q-08 | **CEEDAYS mapping**: which inputs yield which feedback token (2507..2521) is Language-Environment behaviour not in the repo; only `0000` and `2513` are relied on by callers. Target `java.time` port must define the mapping and mark the rest INFERRED. | B-0015 UNRESOLVED; `COTRN02C.cbl:398-401` (per analysis) | plan stop (affects S-08/S-09 parity, not S-01 screens) |
| Q-09 | **RESP/RESP2 text**: exact digits in E-09..E-12 are compiler-dependent (INFERRED). Target should keep the literal parts and append a reason code; byte parity is not a goal. | R-06 | accepted assumption |
| Q-10 | **Date field format**: account dates are `X(10)` moved verbatim; `YYYY-MM-DD` is INFERRED from naming and fixtures; a `LocalDate` mapping may reject bad fixture rows that the legacy screen would show as text. | `COACTVWC.cbl:487-489`, `:507`; R-02, R-04 | wave 1 fixture check |
| Q-11 | **Truncated zip/phone**: legacy map shows 5 of 10 zip chars and 13 of 15 phone chars. Target proposal: show full stored values (deviation). | `COACTVW.bms:290-294`, `:318-321`; R-13 | BA confirmation |
| Q-12 | **Menu options 2..11**: rendered but excluded; how they behave in the target (hidden / "not available" / link to legacy) is B-0010 UNDECIDED. Nothing here specifies them. | `COMEN01C.cbl:145-188`; `COMEN02Y.cpy:31-90` | plan stop |
| Q-13 | **Password store**: legacy compares 8-char plaintext; target state mandates BCrypt upgrade-on-login with the USRSEC stub retained (real SSO deferred). Case-folding (FR-04) must be applied before hashing to preserve behaviour. | `COSGN00C.cbl:135-136`, `:223`; B-0026 | plan stop |
| Q-14 | **`RECEIVE MAP` RESP never checked** in any of the three programs; a MAPFAIL would fall through with stale input. Target validates the request payload instead (no requirement). | R-09 | accepted assumption |

**Message-text caveat**: every message above is an in-source literal except E-09..E-12, whose numeric tails are built from CICS `RESP`/`RESP2` at run time; there is no external message table in this stream.

**Divergence from cross-check branches**: not consulted. This document is derived from `app/` source on the work branch and from `AccountView_analysis.md` only. Line cites in this document were re-verified against source; where the analysis cites differ by one or two lines in §3.3 (e.g. `cbl:473` vs `:474`), the numbers here are the verified ones.

---

## 12. Validation checklist
1. Every requirement has a business trigger and an observable result — pass (25/25).
2. Every claim carries a `<file>:<line>` cite; INFERRED items are labelled — pass.
3. Message-text caveat stated (§5, §11) — pass.
4. Nothing specified past the hard stop (`COACTVWC.cbl:349-352`); excluded programs appear only as exclusions (§1, §7.2, Q-01, Q-12) — pass.
5. Every in-scope program appears in the program index with its requirement ids — pass (4/4).
6. Process type ONLINE: surfaces are screens and AID keys; no batch or subtransaction assumptions applied; `CSUTLDTC` is specified as a callable contract because it is ported in wave 1 (D-0020) — pass.

Feeds: `!mf_stream_migration_plan` (STOP C), then `!mf_program_fr_generation` per program in §10.
