# `COMEN01C` — program functional requirements (`!mf_program_fr_generation`)

| Item | Value |
|---|---|
| Stream | S-01 AccountView (`functional/CardDemo/AccountView_functional_requirement.md`, approved STOP C, D-0033..D-0036) |
| Wave | **3** (`AccountView_migration_plan.md` §6, wave 3 — main menu shell) |
| Process type | ONLINE; surface = **screen `COMEN1A`** (mapset `COMEN01`), role dispatcher / menu shell |
| Inputs consumed | stream FR §3.2, §4 (FR-09..FR-12, FR-25), §5, §6.2, §7; analysis §3, §5; plan §3 (B-0010, B-0032, B-0033), §9 (DV-03), §10 (Q-01 banner, Q-12) |
| Evidence policy | Source-derived (`app/cbl/COMEN01C.cbl`, `app/cpy/COMEN02Y.cpy`, `app/bms/COMEN01.bms`, `app/cpy-bms/COMEN01.CPY`, `app/cpy/COCOM01Y.cpy`, `app/cpy/CSMSG01Y.cpy`); FACT vs INFERRED marked |
| Branch | `devin/1788757216-cardemo-account-view-stream` |

Section order is identical across the four program FRs of this stream.

---

## 1. Identity and role

| Item | Value | Cite |
|---|---|---|
| Program id / transaction | `COMEN01C`, trancode `CM00` | `app/cbl/COMEN01C.cbl:36-37`; `app/csd/CARDDEMO.CSD:399-400` |
| Source | `app/cbl/COMEN01C.cbl` (308 lines) + option table `app/cpy/COMEN02Y.cpy` (11 entries, `OCCURS 12`) | whole files |
| Role in the stream | **dispatch / menu shell**: renders the 11-option main menu from a static table, normalises and validates the typed option, transfers control to the selected program (only option 1 `COACTVWC` is live in S-01), and exits to sign-on on PF3 | `:82-110`, `:114-188`, `:196-203`, `:262-303` |
| Shared program | **Yes — owned by S-01 and ported once for the module**; S-02..S-10 and S-13 inherit the shell and flip their own row of the route table when they land | plan §3.1 B-0033, §6 wave 3 |
| Target units | `service/MenuService` (+ `MenuOption` record, static route table), `api/MenuController` (`GET /api/menu`, `POST /api/menu/select`); Angular `menu` component at `/menu` | plan §3.1 B-0033, §3.2 B-0010, §6 wave 3 |
| Target DTOs | `MenuSelectRequest{option}` -> `MenuSelectionResponse{program, endpoint, implemented, message}`; `GET /api/menu` -> list of `MenuOption{number, name, program, endpoint, implemented, userType}` plus header fields | plan §2, §3.1, §3.2 |

---

## 2. Trigger / caller contract

**Legacy (FACT)**

| Entry | Condition | Behaviour | Cite |
|---|---|---|---|
| `XCTL 'COMEN01C' COMMAREA(CARDDEMO-COMMAREA)` from `COSGN00C` (regular user) | `EIBCALEN > 0`, `CDEMO-PGM-CONTEXT = 0` (`CDEMO-PGM-ENTER`) | set `CDEMO-PGM-REENTER`, clear map, send menu | `COSGN00C.cbl:236-239`; `:86-92` |
| `XCTL PROGRAM('COMEN01C') COMMAREA(...)` from `COACTVWC` PF3 (stream hard stop) | `CDEMO-PGM-CONTEXT = 0` set by the caller | same: menu rendered fresh | `COACTVWC.cbl:345-352`; `:86-92` |
| Pseudo-conversational re-entry | `RETURN TRANSID('CM00') COMMAREA(...)`; `CDEMO-PGM-REENTER` true | `RECEIVE MAP`, then ENTER -> `PROCESS-ENTER-KEY`; PF3 -> `RETURN-TO-SIGNON-SCREEN`; other -> E-03 | `:93-110` |
| Started with **no COMMAREA** (`EIBCALEN = 0`) | direct `CM00` without sign-on | `MOVE 'COSGN00C' TO CDEMO-FROM-PROGRAM`, `XCTL 'COSGN00C'` — the menu refuses to render without a session | `:82-84`, `:196-203` |

COMMAREA fields **read**: `CDEMO-PGM-CONTEXT` (`:87`), `CDEMO-USER-TYPE` (`:136`, only in the unreachable admin guard), `CDEMO-TO-PROGRAM` (`:198`). Fields **written** on dispatch: `CDEMO-FROM-TRANID='CM00'`, `CDEMO-FROM-PROGRAM='COMEN01C'`, `CDEMO-PGM-CONTEXT=0` (`:178-183`; the user-id/type moves at `:181-182` are commented out). PF3 exit issues `XCTL` **without** COMMAREA (`:200-202`).

**Target trigger**: the SPA routes to `/menu` after `AuthResponse.landingTarget` or after Account View Exit; the component calls `GET /api/menu` (render) and `POST /api/menu/select` (option). Both require an authenticated session; without one the backend answers 401 and the SPA routes to `/signon` (this is the target form of the `EIBCALEN = 0` refusal).

---

## 3. Inputs and outputs at field level

### 3.1 Screen `COMEN1A` — INPUT field

| Field | Symbolic / PIC | BMS attributes | Label (verbatim) | Edit rule | Target DTO field | Cite |
|---|---|---|---|---|---|---|
| `OPTION` | `OPTIONI PIC X(2)` | `UNPROT`, length 2 | `'Please select an option :'` | normalised (§5 V-1), must be numeric, `> 0`, `<= 11` | `MenuSelectRequest.option` (`String`, raw as typed, max 2) | `COMEN01.bms:144`; `COMEN01.CPY:132`; `cbl:117-134` |

AID keys: ENTER = select, PF3 = exit to sign-on, other = E-03 (`:93-103`). Target: `Continue`/`Exit` buttons, Enter-in-form, `F3`/`Esc` = Exit (Q-06, DV-03).

### 3.2 Screen `COMEN1A` — DISPLAY fields

| Field | Len | Content | Source | Target | Cite |
|---|---|---|---|---|---|
| `OPTN001..OPTN011` | 40 each | `nn. <name>` — `CDEMO-MENU-OPT-NUM 9(02)` + `'. '` + `CDEMO-MENU-OPT-NAME X(35)`: `01. Account View` … `11. Pending Authorization View` | `COMEN02Y` table | `MenuOption.number` (2-digit) + `name` rendered `nn. name`; rows 2..11 `implemented=false`, visually disabled | `cbl:262-303`; `COMEN02Y.cpy:19-90` |
| `OPTN012` | 40 | blank (table count is 11) | — | not rendered | `COMEN02Y.cpy:21`; `cbl:264` |
| `OPTIONO` | 2 | echo of the **normalised** option after ENTER | `WS-OPTION` | `MenuSelectionResponse.message` context / component keeps normalised value (see FR-10) | `cbl:125` |
| header | — | `'Tran:'` `CM00`, `'Prog:'` `COMEN01C`, `'Date:'` `mm/dd/yy`, `'Time:'` `hh:mm:ss`, titles, `'Main Menu'` | `POPULATE-HEADER-INFO` | header DTO fields from `Clock`; `CM00` informational only (D-0016) | `cbl:240-257`; `COMEN01.bms:33-79` |
| footer | — | `'ENTER=Continue  F3=Exit'` | BMS `INITIAL=` | verbatim | `COMEN01.bms:162` |
| `ERRMSG` | 80 | message of §5 / §7 | `WS-MESSAGE`; colour `ERRMSGC` set only on the excluded option-11/DUMMY paths | `ErrorResponse.message` (400) or `MenuSelectionResponse.message` | `cbl:79-80`, `:213`; `:157`, `:170` |
| **target-only banner** | — | `Administration is not available in this release` when session `userType='A'` | none (legacy routes admins elsewhere) | rendered by the `menu` component from `SessionResponse.userType` — **NEW TEXT**, Q-01 | plan §3.2 B-0009 |

### 3.3 Static data — option table `COMEN02Y` (B-0033)

| # | `CDEMO-MENU-OPT-NAME` (35, verbatim, trailing spaces trimmed here) | `PGMNAME` | `USRTYPE` | S-01 status | Target row | Cite |
|---|---|---|---|---|---|---|
| 01 | `Account View` | `COACTVWC` | `U` | **live** | `{1,'Account View','COACTVWC','/api/accounts/{acctId}',true,'U'}` -> SPA `/accounts/view` | `COMEN02Y.cpy:25-29` |
| 02 | `Account Update` | `COACTUPC` | `U` | excluded | `implemented=false` | `:31-35` |
| 03 | `Credit Card List` | `COCRDLIC` | `U` | excluded | `implemented=false` | `:37-41` |
| 04 | `Credit Card View` | `COCRDSLC` | `U` | excluded | `implemented=false` | `:43-47` |
| 05 | `Credit Card Update` | `COCRDUPC` | `U` | excluded | `implemented=false` | `:49-53` |
| 06 | `Transaction List` | `COTRN00C` | `U` | excluded | `implemented=false` | `:55-59` |
| 07 | `Transaction View` | `COTRN01C` | `U` | excluded | `implemented=false` | `:61-65` |
| 08 | `Transaction Add` | `COTRN02C` | `U` | excluded | `implemented=false` | `:67-71` |
| 09 | `Transaction Reports` | `CORPT00C` | `U` | excluded | `implemented=false` | `:73-77` |
| 10 | `Bill Payment` | `COBIL00C` | `U` | excluded | `implemented=false` | `:79-83` |
| 11 | `Pending Authorization View` | `COPAUS0C` | `U` | excluded (out of module, B-0032) | `implemented=false` | `:85-89` |

`CDEMO-MENU-OPT-COUNT = 11` (`:21`). All `USRTYPE` are `U` (FACT), which is why the admin guard is unreachable (§7).

### 3.4 Outputs other than the screen

| Output | Legacy | Target | Cite |
|---|---|---|---|
| Dispatch | `XCTL PROGRAM(CDEMO-MENU-OPT-PGMNAME(WS-OPTION)) COMMAREA` with `FROM-TRANID/PROGRAM`, `PGM-CONTEXT=0` | `MenuSelectionResponse{program:'COACTVWC', endpoint:'/api/accounts/{acctId}', implemented:true, message:null}`; SPA routes to `/accounts/view` | `:177-187` |
| Exit | `XCTL 'COSGN00C'` **without** COMMAREA | `POST /api/auth/signoff` (session invalidated) then SPA `/signon` | `:96-98`, `:196-203` |
| Session identity | pass-through (not rewritten: `:181-182` commented) | unchanged; identity stays in the HTTP session | `:178-183` |

---

## 4. Functional requirements owned by this program

| Program req | Stream req | Business trigger | Observable result | Source cite |
|---|---|---|---|---|
| MEN-01 | **FR-09** (and the **menu-rendering half of FR-05** — see split) | user arrives at the menu after sign-on or after Exit from Account View | menu with 11 rows `01. Account View` … `11. Pending Authorization View`, header `CM00`/`COMEN01C`, footer `ENTER=Continue  F3=Exit`, option empty, no message | `:85-92`, `:208-220`, `:262-303`; `COMEN02Y.cpy:21-90` |
| MEN-02 | **FR-10** | ENTER with option blank, `0`, non-numeric or `> 11` | E-08 `Please enter a valid option number...`; menu re-shown; option field echoes the normalised 2-digit value (blank -> `00`) | `:117-134`; `COMEN02Y.cpy:21` |
| MEN-03 | **FR-11 (menu half)** — see split | user enters `1` (also ` 1`, `01`, `1 `) and presses ENTER | control transferred to `COACTVWC` with `PGM-CONTEXT=0`; target: `implemented:true`, endpoint `/api/accounts/{acctId}`, SPA to `/accounts/view` | `:117-125`, `:177-187`; `COMEN02Y.cpy:25-29` |
| MEN-04 | **FR-12** | PF3 on the menu | fresh sign-on screen (FR-01); identity dropped (XCTL without COMMAREA) | `:96-98`, `:196-203` |
| MEN-05 | **FR-25 (menu half)** — see split | AID other than ENTER/PF3 on the menu | E-03 `Invalid key pressed. Please see below...`, menu unchanged — **target: DV-03, no invalid-key state** | `:99-102`; `CSMSG01Y.cpy:20-21` |
| MEN-06 | **Q-12 facade** (decided handling of the FR §1 exclusion; not a new business rule) | user selects option 2..11 | rows rendered disabled with verbatim labels; on selection `MenuSelectionResponse{implemented:false, message:'Option not available in this release'}` (NEW TEXT); no navigation | `:145-188` (legacy would XCTL / INQUIRE); plan §3.2 B-0010, §10 Q-12 |

### Split ownership (exact)

| Stream req | `COMEN01C` owns | Other program owns | Rule implemented once? |
|---|---|---|---|
| **FR-05** | rendering the menu when a freshly authenticated user arrives (`:86-92`) = MEN-01 | `COSGN00C` owns authentication, session creation and `landingTarget` (SGN-05) | yes |
| **FR-11** | option normalisation, range validation, table lookup, dispatch decision, `PGM-CONTEXT=0` hand-off (`:117-125`, `:177-187`) = MEN-03 | `COACTVWC` owns the **entry state** it renders on arrival (`COACTVWC.cbl:282-286`, `:353-360`) = ACV-01/FR-13 (`COACTVWC_functional_requirement.md`) | yes: MEN never renders Account View fields; ACV never validates a menu option |
| **FR-25** | E-03 on the **menu** screen (`:99-102`) | `COSGN00C` owns E-03 on the sign-on screen | yes (DV-03 applies to both, asserted per screen) |

Not owned here: FR-01..FR-04, FR-06..FR-08, FR-22 (sign-on); FR-13..FR-21 (Account View); FR-23/FR-24 (date utility).

---

## 5. Business rules and validations

| # | Rule | Behaviour | Blocking? | FACT/INF | Cite |
|---|---|---|---|---|---|
| V-1 | Option normalisation | scan `OPTIONI` from the right for the last non-blank (`WS-IDX`), take `OPTIONI(1:WS-IDX)` into `WS-OPTION-X X(02) JUST RIGHT`, `INSPECT … REPLACING ALL ' ' BY '0'`, move to `WS-OPTION 9(02)`, echo to `OPTIONO`. Hence `'1 '`->`01`, `' 1'`->`01`, `'01'`->`01`, `'  '`->`00`, `'A '`->`0A`, `'12'`->`12` | — | FACT | `:117-125` |
| V-2 | Option validity | `WS-OPTION IS NOT NUMERIC OR WS-OPTION > CDEMO-MENU-OPT-COUNT (11) OR WS-OPTION = ZEROS` -> E-08 | blocking | FACT | `:127-134`; `COMEN02Y.cpy:21` |
| V-3 | **Validate before indexing (target rule, R-07)** | legacy continues after E-08 and evaluates `CDEMO-MENU-OPT-USRTYPE(WS-OPTION)` with an out-of-range or zero subscript (`:136-137`) — undefined behaviour, **not reproduced**: the target returns 400 immediately after V-2 | — | FACT (legacy defect) | `:127-143`; plan §6 wave 3 note |
| V-4 | Admin-only guard | `CDEMO-USRTYP-USER AND CDEMO-MENU-OPT-USRTYPE(WS-OPTION) = 'A'` -> `'No access - Admin Only option... '` — **unreachable**: every table row is `U` | n/a | FACT (dead) | `:136-143`; `COMEN02Y.cpy:29-89` |
| V-5 | Dispatch | option 1 -> `XCTL 'COACTVWC'`; options 2..10 -> XCTL to their program (**excluded**); option 11 -> `INQUIRE PROGRAM` guard (**excluded**, B-0032); `DUMMY*` -> `'is coming soon'` (dead: no such row) | — | FACT | `:145-188` |
| V-6 | Labels | exactly `nn. name` for 11 rows, built from the table, row 12 blank | — | FACT | `:262-303` |
| V-7 | No-session refusal | `EIBCALEN = 0` -> straight to sign-on, no menu | blocking | FACT | `:82-84` |

**Target-side rules (decided)**:

- **B-0033**: the table is a static `List<MenuOption>` in `MenuService`, not a database table; `implemented` is the single strangler switch per option.
- **B-0010 / Q-12**: `POST /api/menu/select` -> option 1 `{program:'COACTVWC', endpoint:'/api/accounts/{acctId}', implemented:true}`; options 2..11 `{implemented:false, message:'Option not available in this release'}` (target-only text, marked NEW TEXT in `CobolMessages`); no deep link to legacy CICS screens. Invalid option -> 400 E-08 with the normalised echo.
- **B-0032**: option 11 behaves like 2..10 in the facade; the runtime `INQUIRE PROGRAM` probe has no target equivalent.
- **Q-01 banner**: when the session `userType='A'`, the component shows `Administration is not available in this release`; menu rows and option handling are otherwise identical (all rows are `U`).
- **Q-14**: request payload validated explicitly; `RECEIVE MAP RESP` (`:231`) is never tested in the legacy.

---

## 6. Data access and boundaries

**Access**: none. The program reads no file (the `WS-USRSEC-FILE` literal at `:39` and `COPY CSUSR01Y` at `:57` are unused). No unit of work.

| Boundary | Register status | Decided target mechanism | Error / timeout contract | Cite |
|---|---|---|---|---|
| **B-0033** menu option table (B10) | DECIDED | static route table `MenuService#options()` / `MenuOption{number,name,program,endpoint,implemented,userType}` in code | n/a (no I/O) | register B-0033; plan §3.1 |
| **B-0010** menu dispatch + hard stop (B5) | DECIDED | `POST /api/menu/select` -> `MenuSelectionResponse`; option 1 live -> `/accounts/view`; 2..11 facade; Exit -> `/signon` with session invalidation; (the Account-View-side Exit -> `/menu` half belongs to `COACTVWC`) | invalid option -> 400 E-08 + normalised echo; no timeout (no I/O) | register B-0010; plan §3.2 |
| **B-0032** option 11 `INQUIRE` guard (B5) | DECIDED (excluded, folded into B-0010 facade) | `implemented=false` for row 11 | — | register B-0032; plan §3.2 |
| **B-0027** COMMAREA (B10) | DECIDED | identity from the HTTP session (`SessionContext`); menu writes nothing to it | session expiry -> 401 -> `/signon` | register B-0027; plan §3.3 |
| **B-0028** pseudo-conversation (B5) | DECIDED | dropped; `GET /api/menu` = enter, `POST /api/menu/select` = re-enter | n/a | register B-0028 |
| **B-0029** BMS `COMEN1A` (B11) | DECIDED | Angular `menu` component, 11 labels verbatim, disabled rows for 2..11 | explicit payload validation (Q-14) | register B-0029 |
| **B-0030** CICS system services (B11) | DECIDED | header date/time from the `Clock` bean (`:240-257`); no `ASSIGN` in this program | n/a | register B-0030 |

No undecided boundary for this program.

---

## 7. Error and edge behavior

| Case | Legacy result | Target result | Class | Cite |
|---|---|---|---|---|
| option `''` / `'  '` | `00` -> E-08, echo `00` | 400 `Please enter a valid option number...`, echo `00` | business (FR-10) | `:117-134` |
| option `0` / `00` | E-08, echo `00` | same | business | `:129` |
| option `12` .. `99` | E-08, echo as typed (e.g. `12`); legacy then indexes the table out of range (undefined) | 400 E-08, echo `12`; **no indexing** (V-3) | business + defect not reproduced | `:128`, `:136-137` |
| option `A`, `1A`, `A1`, `?` | `NOT NUMERIC` -> E-08, echo normalised (`0A`, `1A`, `A1`, `0?`) | same echo, 400 | business | `:127` |
| option `1`, ` 1`, `01`, `1 ` | dispatch to `COACTVWC` | `implemented:true`, SPA `/accounts/view` | business (FR-11 MEN) | `:117-125`, `:184-187` |
| option `2`..`10` | XCTL to excluded programs | `implemented:false`, `Option not available in this release`; stay on `/menu` | approved exclusion facade (Q-12) | `:177-187` |
| option `11` | `INQUIRE PROGRAM('COPAUS0C')`; installed -> XCTL; else red `'This option Pending Authorization View is not installed...'` | same facade as 2..10 (B-0032); the `is not installed` text is **not** carried to the target | excluded | `:147-167` |
| `DUMMY*` row | `'This option … is coming soon ...'` | dead (no such row) — not ported | dead | `:169-176` |
| admin-only guard | unreachable (all rows `U`) | not ported; documented dead | dead | `:136-143` |
| admin user (`A`) on the menu | never happens in legacy (routed to `COADM01C`) | menu renders normally with the Q-01 banner; every option behaves as for `U` | approved (Q-01) | plan §3.2 B-0009 |
| PF3 | `XCTL 'COSGN00C'` without COMMAREA -> fresh sign-on | `POST /api/auth/signoff` + SPA `/signon`; session gone | business (FR-12) | `:96-98`, `:196-203` |
| other AID | E-03, menu re-sent | **DV-03**: no such state | deliberate deviation | `:99-102` |
| `CM00` started without COMMAREA | XCTL to sign-on | unauthenticated `GET /api/menu` -> 401 -> `/signon` | technical (V-7) | `:82-84` |
| `RECEIVE MAP` failure | RESP captured, never tested | payload validation (Q-14) | technical (demoted) | `:226-232` |
| two `SEND MAP`s in one task (E-08 path then guard path) | possible in legacy because `SEND-MENU-SCREEN` does not return control | irrelevant: single HTTP response | technical (demoted) | `:127-143` |
| session expiry | n/a | 401 -> `/signon` | technical | B-0027 |
| restart / rerun | n/a (ONLINE) | n/a | — | — |

### Deliberate deviations owned here

| Id | Screens | Legacy (cite) | Target | Required parity assertion |
|---|---|---|---|---|
| **DV-03** | **`COMEN1A` only** (sign-on and Account View halves are in their own program FRs) | `WHEN OTHER` -> E-03 re-sends the menu (`COMEN01C.cbl:99-102`) | `Continue`/`Exit` buttons, Enter submits, `F3`/`Esc` = Exit; no invalid-key message | `menu.component.spec.ts` asserts an unbound key (e.g. `F5`) produces no change and no message, tagged DV-03 / FR-25(MEN) |

### Q-resolutions relevant here (approved, plan §10)

- **Q-12** options 2..11 rendered disabled with verbatim labels; selection returns `implemented:false` + `Option not available in this release`.
- **Q-01** admin banner text on `/menu` (rendered by this program's component; the user type comes from the session).
- **Q-06** buttons + `F3`/`Esc` (basis of DV-03).
- **Q-14** explicit request validation.

---

## 8. Hard-stop boundary

This program's delegation boundary is the `XCTL` at `:184-187` — in the target, `MenuSelectionResponse{implemented:true}` and the SPA navigating to `/accounts/view`. `COMEN01C` is **not responsible for**:

- the Account View entry state, account-id edits, lookups or display (FR-13..FR-21) — `COACTVWC`;
- the return path **from** Account View to the menu (FR-21, the stream hard stop at `COACTVWC.cbl:349-352`) — `COACTVWC` initiates it; this program merely renders MEN-01 again when re-entered with `PGM-CONTEXT=0`;
- authentication, password handling or session creation (FR-02..FR-07, FR-22) — `COSGN00C`;
- anything behind options 2..11 (`COACTUPC`, `COCRDLIC`, …, `COPAUS0C`) — **excluded** from S-01; the facade is the only behaviour specified;
- any admin menu (`COADM01C`) — excluded; only the Q-01 banner is specified.

Nothing here specifies past the stream hard stop.

---

## 9. Acceptance criteria

Backend criteria are `MenuService`/`MenuController` tests with an authenticated test session; UI criteria are in `menu.component.spec.ts`. Each is traceable to the program requirement (and stream requirement) in column 2.

| Id | Req | Given | When | Then |
|---|---|---|---|---|
| AC-MEN-01 | MEN-01 / FR-09, FR-05(MEN) | authenticated session `userType='U'` | `GET /api/menu` and `/menu` rendered | exactly 11 options, labels verbatim `01. Account View`, `02. Account Update`, `03. Credit Card List`, `04. Credit Card View`, `05. Credit Card Update`, `06. Transaction List`, `07. Transaction View`, `08. Transaction Add`, `09. Transaction Reports`, `10. Bill Payment`, `11. Pending Authorization View`; header `CM00`, `COMEN01C`, date/time from the pinned `Clock`; footer `ENTER=Continue  F3=Exit`; option field empty; no message; no banner |
| AC-MEN-02 | MEN-06 / Q-12 | same | inspect rows | row 1 enabled `implemented=true`; rows 2..11 `implemented=false`, visually disabled, labels unchanged |
| AC-MEN-03 | MEN-02 / FR-10 | same | `POST /api/menu/select {option:''}` | 400 `Please enter a valid option number...`; normalised echo `00`; UI shows `00` in the option field |
| AC-MEN-04 | MEN-02 / FR-10 | same | `{option:'0'}`, `{option:'12'}`, `{option:'A'}` | 400 E-08 with echoes `00`, `12`, `0A` respectively |
| AC-MEN-05 | V-3 / R-07 | same | `{option:'99'}` | 400 E-08; no exception, no table access beyond the count check (verified by unit test on `MenuService`) |
| AC-MEN-06 | MEN-03 / FR-11(MEN) | same | `{option:'1'}`, `{option:' 1'}`, `{option:'01'}`, `{option:'1 '}` | each -> 200 `MenuSelectionResponse{program:'COACTVWC', endpoint:'/api/accounts/{acctId}', implemented:true}`; SPA navigates to `/accounts/view` |
| AC-MEN-07 | MEN-06 / Q-12, B-0032 | same | `{option:'2'}` … `{option:'11'}` | each -> 200 `{implemented:false, message:'Option not available in this release'}`; program name equals the table row's `PGMNAME`; SPA stays on `/menu` and shows the message |
| AC-MEN-08 | MEN-04 / FR-12 | same | Exit button / `F3` / `Esc` | `POST /api/auth/signoff`; session invalidated; SPA at `/signon` in FR-01 state; subsequent `GET /api/menu` -> 401 |
| AC-MEN-09 | MEN-05 / FR-25(MEN), DV-03 | `/menu` | press `F5` (unbound) | nothing changes, no message; test tagged DV-03 |
| AC-MEN-10 | Q-01 | authenticated session `userType='A'` | `/menu` rendered | same 11 rows as AC-MEN-01 **plus** banner `Administration is not available in this release`; option 1 works as for `U` |
| AC-MEN-11 | V-7 / B-0027 | no session | `GET /api/menu` or `POST /api/menu/select` | 401; SPA routes to `/signon` |
| AC-MEN-12 | B-0027 (DV-02 counterpart) | authenticated `USER0001` | menu -> option 1 -> Account View -> Exit -> menu | `GET /api/auth/session` still returns `USER0001`/`U` (identity intact after the round trip) |
| AC-MEN-13 | D-0016 | any response | inspect URLs | `CM00` appears only in header text, never in a route |

---

## 10. Open questions and assumptions

| Id | Item | Status | Owner / when |
|---|---|---|---|
| A-MEN-1 | Echo of the normalised option for non-numeric input (`0A`) is FACT for the legacy; how the UI displays it (in the field vs. beside the message) is a wave-3 UI choice — the value itself must be `0A` | assumption | wave 3 |
| A-MEN-2 | Row 12 of the `OCCURS 12` table is unused (count 11); the target table has 11 entries and no placeholder | assumption (FACT on count) | wave 3 |
| A-MEN-3 | Whether later streams add rows/columns to `MenuOption` (e.g. admin options with `userType='A'`) is out of S-01 scope; the `userType` field is carried so that the dead guard V-4 can be revived by S-11 without changing the record shape | note | S-11 |
| A-MEN-4 | `DFHRED`/`DFHGREEN` colours on the excluded option-11/DUMMY messages are not carried (paths excluded/dead) | accepted | — |
| — | Undecided boundary | **none** (B-0010, B-0027, B-0028, B-0029, B-0030, B-0032, B-0033 all DECIDED) | — |

**Divergence from cross-check branches**: not consulted. This document derives from `app/` source on the work branch and the approved stream artifacts only.
