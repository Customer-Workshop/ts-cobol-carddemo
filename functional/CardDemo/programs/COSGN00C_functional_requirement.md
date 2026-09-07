# `COSGN00C` — program functional requirements (`!mf_program_fr_generation`)

| Item | Value |
|---|---|
| Stream | S-01 AccountView (`functional/CardDemo/AccountView_functional_requirement.md`, approved STOP C, D-0033..D-0036) |
| Wave | **2** (`AccountView_migration_plan.md` §6, wave 2 — sign-on) |
| Process type | ONLINE; surface = **screen `COSGN0A`** (mapset `COSGN00`), role entry / authenticator / router |
| Inputs consumed | stream FR §3.1, §4 (FR-01..FR-08, FR-22, FR-25), §5, §6.1, §7; analysis §3, §4.5, §5; plan §3, §9 (DV-03), §10 (Q-01, Q-13) |
| Evidence policy | Source-derived (`app/cbl/COSGN00C.cbl`, `app/bms/COSGN00.bms`, `app/cpy-bms/COSGN00.CPY`, `app/cpy/COCOM01Y.cpy`, `app/cpy/CSUSR01Y.cpy`, `app/cpy/CSMSG01Y.cpy`, `app/cpy/COTTL01Y.cpy`, `app/cpy/CSDAT01Y.cpy`); FACT vs INFERRED marked |
| Branch | `devin/1788757216-cardemo-account-view-stream` |

Section order is identical across the four program FRs of this stream.

---

## 1. Identity and role

| Item | Value | Cite |
|---|---|---|
| Program id / transaction | `COSGN00C`, trancode `CC00` | `app/cbl/COSGN00C.cbl:36-37`; `app/csd/CARDDEMO.CSD:378-379` |
| Source | `app/cbl/COSGN00C.cbl` (260 lines) | whole file |
| Role in the stream | **entry / validator / router**: renders the sign-on screen, edits User ID and Password, reads `USRSEC`, establishes the session identity in the COMMAREA and transfers control to the main menu (`COMEN01C`) for regular users | `:80-102`, `:117-140`, `:207-257` |
| Shared program | **Yes — owned by S-01 and ported once for the module** (every online stream enters through it; inventory §8, D-0019) | stream FR §1 |
| Target units | `api/AuthController` (`POST /api/auth/signon`, `GET /api/auth/session`, `POST /api/auth/signoff`), `service/AuthService`, `security/SecurityConfig`, `security/SecurityUserDetailsService`, upgrade-on-login `AuthenticationSuccessHandler`; Angular `sign-on` component at route `/signon` | plan §6 wave 2; §3.2 B-0009; §3.3 B-0026 |
| Target DTOs | `AuthRequest{userId, password}` -> `AuthResponse{userId, userType, landingTarget}`; `SessionResponse`; `ErrorResponse{message}` on failure | plan §2 (record DTOs), §3.2 B-0009 |

---

## 2. Trigger / caller contract

**Legacy (FACT)**

| Entry | Condition | Behaviour | Cite |
|---|---|---|---|
| Terminal starts `CC00` | `EIBCALEN = 0` (no COMMAREA) | clear map, cursor to User ID (`USERIDL = -1`), send sign-on screen | `:80-84` |
| `XCTL 'COSGN00C'` from `COMEN01C` PF3 | issued **without** COMMAREA, so again `EIBCALEN = 0` | same fresh sign-on screen (this is how FR-12 lands on FR-01) | `COMEN01C.cbl:196-203`; `:80-84` |
| Pseudo-conversational re-entry | `EIBCALEN > 0` after `RETURN TRANSID('CC00') COMMAREA(CARDDEMO-COMMAREA)` | `EVALUATE EIBAID`: ENTER -> `PROCESS-ENTER-KEY`; PF3 -> thank-you text; other -> E-03 | `:85-102` |

Layouts: `CARDDEMO-COMMAREA` (`app/cpy/COCOM01Y.cpy:19-44`) — the program **writes** `CDEMO-FROM-TRANID`, `CDEMO-FROM-PROGRAM`, `CDEMO-USER-ID`, `CDEMO-USER-TYPE`, `CDEMO-PGM-CONTEXT` (`:224-228`) and never reads any incoming COMMAREA field for a decision (the only test is `EIBCALEN`). Outgoing: `XCTL PROGRAM('COMEN01C') COMMAREA(CARDDEMO-COMMAREA)` (`:236-239`) or the excluded `XCTL 'COADM01C'` (`:231-234`).

**Target trigger**: browser loads `/signon` (initial render, FR-01); the component `POST`s `AuthRequest` to `/api/auth/signon`. Success creates the server-side HTTP session (identity only, B-0027) and returns `AuthResponse.landingTarget = '/menu'`; the SPA navigates there. Failure returns 401 with the verbatim legacy text (B-0009, B-0026).

---

## 3. Inputs and outputs at field level

### 3.1 Screen `COSGN0A` — INPUT fields (`UNPROT`)

| Field | Symbolic / PIC | BMS attributes | Label (verbatim) | Edit rule in this program | Target DTO field | Cite |
|---|---|---|---|---|---|---|
| `USERID` | `USERIDI PIC X(8)` | `UNPROT`, `IC` (initial cursor), length 8 | `'User ID     :'`, hint `'(8 Char)'` | blank/low-values -> E-01; else `UPPER-CASE` -> `WS-USER-ID` and `CDEMO-USER-ID` | `AuthRequest.userId` (max 8, upper-cased server-side) | `COSGN00.bms:155-169`; `COSGN00.CPY:72`; `cbl:118-122`, `:132-134` |
| `PASSWD` | `PASSWDI PIC X(8)` | `UNPROT`, `DRK` (non-display), length 8 | `'Password    :'`, hint `'(8 Char)'` | blank/low-values -> E-02; else `UPPER-CASE` -> `WS-USER-PWD` | `AuthRequest.password` (max 8, upper-cased before compare, Q-13); rendered `type=password` | `COSGN00.bms:174-189`; `COSGN00.CPY:78`; `cbl:123-127`, `:135-136` |

AID keys (input): ENTER, PF3, other (`:85-95`). Target: `Sign on` button / Enter-in-form, `Exit` button + `F3`/`Esc` (Q-06, DV-03).

### 3.2 Screen `COSGN0A` — DISPLAY fields

| Field | Len | Label / content | Source | Target | Cite |
|---|---|---|---|---|---|
| `TRNNAME` | 4 | `'Tran :'` -> `CC00` | `WS-TRANID` | header `tranId` (informational; **must not** appear in any URL, D-0016) | `cbl:37`, `:183`; `bms:33` |
| `PGMNAME` | 8 | `'Prog :'` -> `COSGN00C` | `WS-PGMNAME` | header `programName` | `cbl:36`, `:184`; `bms:56` |
| `CURDATE` | 8 | `'Date :'` -> `mm/dd/yy` | `FUNCTION CURRENT-DATE` via `CSDAT01Y` | header `currentDate` from server `Clock` | `cbl:179`, `:186-190`; `CSDAT01Y.cpy:30-35`; `bms:46-51` |
| `CURTIME` | 8 | `'Time :'` -> `hh:mm:ss` | same | header `currentTime` | `cbl:192-196`; `CSDAT01Y.cpy:36-41`; `bms:69-74` |
| `APPLID` | 8 | `'AppID:'` | `EXEC CICS ASSIGN APPLID` | property `carddemo.applid` | `cbl:198-200`; `bms:79` |
| `SYSID` | 8 | `'SysID:'` | `EXEC CICS ASSIGN SYSID` | property `carddemo.sysid` | `cbl:202-204`; `bms:88` |
| `TITLE01` / `TITLE02` | 40 / 40 | `'      AWS Mainframe Modernization       '` / `'              CardDemo                  '` | `CCDA-TITLE01/02` | header titles verbatim | `COTTL01Y.cpy:18-22`; `cbl:181-182` |
| constant text | — | banner rows, `'Type your User ID and Password, then press ENTER'`, footer `'ENTER=Sign-on  F3=Exit'` | BMS `INITIAL=` | rendered verbatim (footer wording kept even though keys are buttons — Q-06) | `bms:98-149`, `:205` |
| `ERRMSG` | 80 | message of §5 / §7, spaces when none | `WS-MESSAGE X(80)` | `ErrorResponse.message` (401/400/500) or empty | `cbl:38`, `:77-78`, `:149` |

### 3.3 Data read

| Store | Record / key | Fields used | Dictionary (analysis §4.5) | Cite |
|---|---|---|---|---|
| `USRSEC` (B-0006) | `SEC-USER-DATA` 80 bytes, `RIDFLD(WS-USER-ID X(08))` | `SEC-USR-PWD X(08)` (compare), `SEC-USR-TYPE X(01)` (`A`/`U`) | table `users`: `sec_usr_id VARCHAR(8) PK`, `sec_usr_pwd_hash VARCHAR(60)`, `sec_usr_pwd_legacy VARCHAR(8)` nullable, `sec_usr_type CHAR(1)` | `cbl:211-219`, `:223`, `:227`; `CSUSR01Y.cpy:18-22`; plan §4.1 |

### 3.4 Outputs other than the screen

| Output | Legacy | Target | Cite |
|---|---|---|---|
| Session identity | COMMAREA `CDEMO-USER-ID`, `CDEMO-USER-TYPE`, `CDEMO-PGM-CONTEXT=0`, `CDEMO-FROM-TRANID='CC00'`, `CDEMO-FROM-PROGRAM='COSGN00C'` | HTTP session attributes `userId`, `userType` (`SessionContext`); `AuthResponse{userId, userType, landingTarget}` | `cbl:224-228`; plan §3.3 B-0027 |
| Transfer of control | `XCTL COMEN01C` (regular) / `XCTL COADM01C` (admin, excluded) | `landingTarget='/menu'` for **both** `U` and `A` (Q-01) | `cbl:230-239` |
| Exit text | `SEND TEXT FROM(WS-MESSAGE)` `'Thank you for using CardDemo application...'` then `RETURN` without TRANSID (conversation ends) | plain confirmation page; session invalidated (`POST /api/auth/signoff`) | `cbl:88-90`, `:162-172`; `CSMSG01Y.cpy:18-19` |
| Password upgrade (target-only side effect) | none | on successful legacy-plaintext match, rewrite `sec_usr_pwd_hash` (BCrypt) and clear `sec_usr_pwd_legacy` exactly once | plan §3.3 B-0026, Q-13 |

---

## 4. Functional requirements owned by this program

| Program req | Stream req | Business trigger | Observable result | Source cite |
|---|---|---|---|---|
| SGN-01 | **FR-01** | user opens the application (no prior state) or arrives from menu Exit | sign-on screen: empty User ID with focus, masked Password, header `CC00`/`COSGN00C`, date `mm/dd/yy`, time `hh:mm:ss`, APPLID, SYSID, footer `'ENTER=Sign-on  F3=Exit'`, no message | `:80-84`, `:145-157`, `:177-204`; `COSGN00.bms:155-205` |
| SGN-02 | **FR-02** | ENTER with User ID blank | E-01 `Please enter User ID ...`, focus User ID, **no user-store read** | `:117-122`, `:138-140` |
| SGN-03 | **FR-03** | User ID present, Password blank | E-02 `Please enter Password ...`, focus Password, no user-store read | `:123-127`, `:138-140` |
| SGN-04 | **FR-04** | id/password typed in any letter case | both upper-cased before lookup and compare (`admin`≡`ADMIN`) | `:132-136` |
| SGN-05 | **FR-05 (sign-on half)** — see split below | id exists, password matches, type `U` | identity established (user id, type `U`); control handed to the menu, which renders FR-09 | `:221-229`, `:235-239` |
| SGN-06 | **FR-06** | id exists, password differs | E-06 `Wrong Password. Try again ...`, focus Password, stays on sign-on | `:241-246` |
| SGN-07 | **FR-07** | id not in store (RESP 13) | E-07 `User not found. Try again ...`, focus User ID | `:247-251` |
| SGN-08 | **FR-08** | F3 on the sign-on screen | E-14 text `Thank you for using CardDemo application...` shown as plain text; conversation ends | `:88-90`, `:162-172`; `CSMSG01Y.cpy:18-19` |
| SGN-09 | **FR-22** | store read fails (RESP other than 0/13) | E-13 `Unable to verify the User ...`, focus User ID | `:252-256` |
| SGN-10 | **FR-25 (sign-on half)** — see split below | AID other than ENTER/PF3 on the sign-on screen | E-03 `Invalid key pressed. Please see below...`, screen unchanged — **target: DV-03, no invalid-key state** | `:91-94`; `CSMSG01Y.cpy:20-21` |

### Split ownership (exact)

| Stream req | `COSGN00C` owns | `COMEN01C` owns | Rule implemented once? |
|---|---|---|---|
| **FR-05** | authentication (`SEC-USR-PWD = WS-USER-PWD`, `:223`), session establishment (`:224-228`), user-type routing decision (`:230-239`) and the target `landingTarget` | rendering the menu on arrival (`COMEN01C.cbl:85-92`) — documented as MEN-01/FR-09 in `COMEN01C_functional_requirement.md` | yes: SGN never renders menu rows; MEN never checks credentials |
| **FR-25** | E-03 on the **sign-on** screen (`:91-94`) | E-03 on the **menu** screen (`COMEN01C.cbl:99-102`) | yes: same text constant `CCDA-MSG-INVALID-KEY`, but each screen's trigger belongs to its own program; under DV-03 neither is implemented as a state — each program's UI slice simply has no invalid-key path and asserts that (Q-06) |

Not owned here (explicitly): FR-09..FR-12 (menu), FR-13..FR-21 (Account View), FR-23/FR-24 (date utility).

---

## 5. Business rules and validations

Order matters and is **the** parity requirement of the plan for wave 2 ("validation order of `COSGN00C.cbl:117-136`").

| # | Rule | Behaviour | Blocking? | FACT/INF | Cite |
|---|---|---|---|---|---|
| V-1 | User ID mandatory | `USERIDI = SPACES OR LOW-VALUES` -> E-01, cursor User ID, `WS-ERR-FLG='Y'` so the read is skipped | blocking | FACT | `:118-122`, `:138-140` |
| V-2 | Password mandatory (only evaluated if V-1 passed — `EVALUATE TRUE` first `WHEN`) | `PASSWDI = SPACES OR LOW-VALUES` -> E-02, cursor Password, read skipped | blocking | FACT | `:123-127` |
| V-3 | Case folding | `FUNCTION UPPER-CASE` on both fields before lookup/compare | — | FACT | `:132-136` |
| V-4 | Lookup by 8-char id | `READ USRSEC RIDFLD(WS-USER-ID)` | — | FACT | `:211-219` |
| V-5 | Password match | byte compare `SEC-USR-PWD = WS-USER-PWD` (8 chars, both upper-cased; trailing spaces significant only insofar as both are 8-char padded) | mismatch blocking (E-06) | FACT | `:223` |
| V-6 | User type routing | `CDEMO-USRTYP-ADMIN` (`'A'`) -> `COADM01C` (excluded); else -> `COMEN01C` | — | FACT | `:230-239`; `COCOM01Y.cpy:27-28` |
| V-7 | No lockout, no attempt counter, no audit record | repeated failures just re-show the screen | — | FACT (absence) | `:207-257` |
| V-8 | No length/charset edit beyond blank test | any 1-8 char value is looked up as typed (upper-cased); shorter values are space-padded by the map | — | FACT | `:118-136` |

**Target-side rules (decided)**:

- Q-13 / B-0026: password column holds a BCrypt hash; the **upper-cased** input is compared through `DelegatingPasswordEncoder`; a row still carrying `sec_usr_pwd_legacy` is verified once against that plaintext (upper-cased compare, V-5 semantics) and rewritten as a hash, legacy column cleared; the legacy value is never returned by any endpoint.
- Q-01 / B-0009: type `A` **authenticates normally** and receives `landingTarget='/menu'`; the menu shows the banner `Administration is not available in this release` (that banner is rendered by the menu component and is documented in `COMEN01C_functional_requirement.md`; this program only carries `userType='A'` into the session and response). No admin route exists in S-01.
- Q-14 / B-0029: request validation (`@NotNull` presence) replaces the untested `RECEIVE MAP RESP` (`:113`); the *blank* checks V-1/V-2 stay service-level so the message texts stay verbatim.

---

## 6. Data access and boundaries

**Access**: one keyed read of `USRSEC`, read-only, no unit of work, no commit/rollback (`:211-219`; analysis §5.2 — no `WRITE/REWRITE/DELETE` in the program).

| Boundary | Register status | Decided target mechanism | Error / timeout contract | Cite |
|---|---|---|---|---|
| **B-0006** `USRSEC` (B4 VSAM KSDS) | DECIDED | PostgreSQL table `users` (entity `SecurityUser`), `repository/SecurityUserRepository#findById` behind `SecurityUserDetailsService`; hash + nullable legacy plaintext | RESP 13 -> **E-07** (401); password mismatch -> **E-06** (401); other RESP / JDBC failure -> **E-13** (500). No lockout, no retry (parity). No explicit timeout beyond the JDBC/pool default; a timeout surfaces as E-13 | register B-0006; plan §3.1 |
| **B-0026** sign-on / RACF substitute (B11) | DECIDED (deferral of real SSO with re-entry: R-0002 answered, or S-11 planned, or non-demo data) | Spring Security session cookie + BCrypt upgrade-on-login; upper-case before compare | 401 with verbatim E-06/E-07; E-13 on store failure; second successful sign-on replaces the session | register B-0026; plan §3.3 |
| **B-0009** sign-on XCTL (B5) | DECIDED | `POST /api/auth/signon` -> `AuthResponse{userId,userType,landingTarget}`; SPA routes to `landingTarget` (`/menu` for `U` and `A`); `landingTarget` is the strangler point for the future admin menu | 401 with E-06/E-07 text; **no redirect on failure** | register B-0009; plan §3.2 |
| **B-0027** COMMAREA (B10) | DECIDED | server-side HTTP session holds `userId`, `userType` only (`SessionContext`) | session expiry -> 401, SPA routes to `/signon` | register B-0027; plan §3.3 |
| **B-0028** pseudo-conversation (B5) | DECIDED | dropped; each request complete in itself; `EIBCALEN=0` render == initial `GET /signon` | n/a | register B-0028 |
| **B-0029** BMS `COSGN0A` (B11) | DECIDED | Angular `sign-on` component, labels verbatim, `DRK` -> `type=password`, `IC` -> autofocus; `SEND TEXT` -> plain confirmation page | explicit payload validation (Q-14) | register B-0029 |
| **B-0030** CICS system services (B11) | DECIDED | `carddemo.applid`, `carddemo.sysid` properties; `Clock` bean for `mm/dd/yy` / `hh:mm:ss` | n/a | register B-0030 |

No undecided boundary for this program.

---

## 7. Error and edge behavior

| Case | Legacy result | Target result | Class | Cite |
|---|---|---|---|---|
| blank User ID (also all low-values) | E-01, cursor User ID, no read | 400 `Please enter User ID ...`; focus User ID; repository not called | business | `:118-122` |
| blank Password | E-02, cursor Password, no read | 400 `Please enter Password ...`; focus Password | business | `:123-127` |
| both blank | E-01 only (first `WHEN` wins) | E-01 only | business (order) | `:117-128` |
| mixed-case credentials | upper-cased, then normal flow | same | business | `:132-136` |
| unknown user | E-07, cursor User ID | 401 `User not found. Try again ...` | business | `:247-251` |
| wrong password | E-06, cursor Password (note: `WS-ERR-FLG` is **not** set on this path — no observable effect) | 401 `Wrong Password. Try again ...` | business | `:241-246` |
| valid `U` user | COMMAREA written, `XCTL COMEN01C` | 200 `AuthResponse{userId, 'U', '/menu'}`, session created | business | `:221-239` |
| valid `A` user | `XCTL COADM01C` (**excluded**) | 200 `AuthResponse{userId, 'A', '/menu'}` — Q-01; no admin route | approved exclusion handling | `:230-234` |
| store failure (RESP not 0/13) | E-13, cursor User ID | 500 `Unable to verify the User ...` | technical, surfaced as business text (FR-22) | `:252-256` |
| PF3 | `SEND TEXT` thank-you, `RETURN` without TRANSID | confirmation page; `POST /api/auth/signoff` invalidates any session | business (FR-08) | `:88-90`, `:162-172` |
| any other AID | E-03, screen re-sent | **DV-03**: no such state; buttons + `F3`/`Esc` only | deliberate deviation | `:91-94` |
| `RECEIVE MAP` failure | `RESP` captured, never tested — stale input processed | request payload validated explicitly (Q-14) | technical (demoted) | `:110-115`; R-09 |
| user id > 8 chars / password > 8 chars | impossible on the 3270 map (field length 8) | `@Size(max=8)` -> 400 with a **target-only** validation message (not a legacy literal; mark NEW TEXT in `CobolMessages` if a text is added) | technical | `COSGN00.CPY:72`, `:78` |
| session expiry before next request | n/a (COMMAREA travels with the terminal) | 401 -> SPA to `/signon` (B-0027) | technical | plan §3.3 |
| restart / rerun | n/a (ONLINE) | n/a | — | — |

### Deliberate deviations owned here

| Id | Screens | Legacy (cite) | Target | Required parity assertion |
|---|---|---|---|---|
| **DV-03** | **`COSGN0A` only** (the menu and Account View halves are documented in their own program FRs) | `WHEN OTHER` -> E-03 `Invalid key pressed. Please see below...` re-sends the sign-on screen (`COSGN00C.cbl:91-94`; `CSMSG01Y.cpy:20-21`) | `Sign on`/`Exit` buttons, Enter submits, `F3` and `Esc` bound to Exit; no invalid-key message exists | `sign-on.component.spec.ts` asserts that pressing an unbound key (e.g. `F5`) changes nothing and shows no message, tagged DV-03 / FR-25(SGN); `CobolMessages` still carries E-03 verbatim for traceability |

### Q-resolutions relevant here (approved, plan §10)

- **Q-01** admin lands on `/menu` (banner rendered by the menu; this program supplies `userType`).
- **Q-13** BCrypt with upper-case-before-compare and one-time upgrade of legacy plaintext.
- **Q-06** buttons + `F3`/`Esc` (basis of DV-03).
- **Q-14** explicit request validation replaces the unchecked `RECEIVE MAP RESP`.

---

## 8. Hard-stop boundary

This program's delegation boundary is the `XCTL` at `:236-239` (regular user) — in the target, returning `landingTarget='/menu'`. `COSGN00C` is **not responsible for**:

- rendering the menu, its 11 rows or the admin banner (FR-09, Q-01 banner) — `COMEN01C`;
- option validation and dispatch (FR-10, FR-11) — `COMEN01C`;
- anything the admin menu `COADM01C` would do — **excluded** from S-01 (`:231-234`; stream FR §1);
- Account View (FR-13..FR-21) — `COACTVWC`;
- keeping identity alive across later screens — the session model (B-0027, wave 1) does that; this program only *creates* the identity.

It is upstream of the stream hard stop (`COACTVWC.cbl:349-352`) and nothing here specifies past it. The only path that re-enters this program from the stream is the menu Exit `XCTL 'COSGN00C'` without COMMAREA (`COMEN01C.cbl:196-203`), which lands on SGN-01 unchanged.

---

## 9. Acceptance criteria

Traceable to the program requirement (and stream requirement) in column 2. Backend criteria run on Testcontainers PostgreSQL 16 with the fixture user set (`app/data/EBCDIC/AWS.M2.CARDDEMO.USRSEC.PS`, decoded IBM037); UI criteria run in the Angular component spec.

| Id | Req | Given | When | Then |
|---|---|---|---|---|
| AC-SGN-01 | SGN-01 / FR-01 | no session | `/signon` rendered | empty User ID with focus, password input `type=password`, header shows `CC00`, `COSGN00C`, date `mm/dd/yy`, time `hh:mm:ss` from the pinned `Clock`, `carddemo.applid`, `carddemo.sysid`, titles verbatim, footer `ENTER=Sign-on  F3=Exit`; no message |
| AC-SGN-02 | SGN-02 / FR-02 | `/signon` | `POST /api/auth/signon {userId:'', password:'X'}` | 400, message `Please enter User ID ...`; `SecurityUserRepository` not invoked; UI focuses User ID |
| AC-SGN-03 | SGN-03 / FR-03 | `/signon` | `{userId:'USER0001', password:''}` | 400 `Please enter Password ...`; repository not invoked; focus Password |
| AC-SGN-04 | SGN-02/03 order | `/signon` | `{userId:'', password:''}` | only `Please enter User ID ...` |
| AC-SGN-05 | SGN-04 / FR-04 | fixture user `USER0001`/`PASSWORD` (type `U`) | `{userId:'user0001', password:'password'}` | 200, identical to the upper-case call: `AuthResponse{userId:'USER0001', userType:'U', landingTarget:'/menu'}` |
| AC-SGN-06 | SGN-05 / FR-05(SGN) | same user | correct credentials | 200 `AuthResponse{…,'U','/menu'}`; a session cookie is set; `GET /api/auth/session` returns `userId='USER0001'`, `userType='U'`; SPA navigates to `/menu` |
| AC-SGN-07 | SGN-06 / FR-06 | same user | wrong password | 401 `Wrong Password. Try again ...`; no session created; UI stays on `/signon`, focus Password |
| AC-SGN-08 | SGN-07 / FR-07 | id not in `users` | any password | 401 `User not found. Try again ...`; focus User ID |
| AC-SGN-09 | SGN-09 / FR-22 | repository throws (simulated DB outage) | correct-looking credentials | 500 `Unable to verify the User ...`; focus User ID; no stack trace in the body |
| AC-SGN-10 | SGN-08 / FR-08 | `/signon` (with or without an existing session) | Exit button / `F3` / `Esc` | confirmation page with `Thank you for using CardDemo application...`; `POST /api/auth/signoff` called; any session invalidated; browser back does not restore an authenticated state |
| AC-SGN-11 | SGN-10 / FR-25(SGN), DV-03 | `/signon` | press `F5` (unbound) | nothing changes, no message; test tagged DV-03 |
| AC-SGN-12 | Q-01 / B-0009 | fixture admin user (type `A`) | correct credentials | 200 `AuthResponse{…, userType:'A', landingTarget:'/menu'}`; session holds `userType='A'`; no `/admin/**` route exists |
| AC-SGN-13 | Q-13 / B-0026 | user row with `sec_usr_pwd_legacy='PASSWORD'`, `sec_usr_pwd_hash NULL` | correct credentials, then repeat | first call 200 and afterwards `sec_usr_pwd_hash` is a BCrypt hash, `sec_usr_pwd_legacy IS NULL`; second call 200 against the hash; the hash is written **exactly once** |
| AC-SGN-14 | V-7 / B-0026 | any user | 10 consecutive wrong passwords then the right one | the 11th call succeeds (no lockout) |
| AC-SGN-15 | B-0027 | authenticated session | session expires / invalidated, then any `/api/**` call | 401; SPA routes to `/signon` |
| AC-SGN-16 | D-0016 | any response | inspect URLs | `CC00` appears only in the header text, never in a route or endpoint |

---

## 10. Open questions and assumptions

| Id | Item | Status | Owner / when |
|---|---|---|---|
| A-SGN-1 | The 400 message for over-length input (>8 chars) has no legacy literal; whether to reuse E-01/E-02 or add a NEW TEXT is a wave-2 choice — recommend a NEW TEXT marked as such in `CobolMessages` | assumption | wave 2 |
| A-SGN-2 | `DFHRED`/attribute constants come from the absent `DFHBMSCA` copybook; cursor placement (`-1` length trick) is interpreted as focus — **INFERRED** (R-12) | accepted | wave 2 UI |
| A-SGN-3 | Fixture user set is the EBCDIC image `AWS.M2.CARDDEMO.USRSEC.PS` decoded with IBM037 by the importer; until R-0001 is answered it is the extract | carried assumption (R-0001) | wave 1 importer |
| A-SGN-4 | Real SSO is deferred (B-0026); re-entry when R-0002 is answered, S-11 is planned, or a non-demo environment holds real users | decided deferral | orchestrator |
| — | Undecided boundary | **none** (B-0006, B-0009, B-0026, B-0027, B-0028, B-0029, B-0030 all DECIDED) | — |

**Divergence from cross-check branches**: not consulted. This document derives from `app/` source on the work branch and the approved stream artifacts only.
