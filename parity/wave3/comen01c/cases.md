# COMEN01C — source-derived parity cases (written BEFORE reading `backend/` or `frontend/`)

Oracle: `app/cbl/COMEN01C.cbl` (cbl), `app/cpy/COMEN02Y.cpy` (tbl), `app/bms/COMEN01.bms` (bms), `app/cpy-bms/COMEN01.CPY`,
`app/cpy/CSMSG01Y.cpy`, `app/cpy/COCOM01Y.cpy`, `app/cpy/COTTL01Y.cpy`, `app/cpy/CSDAT01Y.cpy`,
`functional/CardDemo/programs/COMEN01C_functional_requirement.md` (FR). No customer-supplied cases exist (none in repo, none from
orchestrator). GnuCOBOL differential: not possible for the CICS program (`07_runbook.md` §1); the pure-COBOL normalisation block
`cbl:117-125` IS replayed under GnuCOBOL as a second oracle (`norm_oracle.cbl`).

## Normalisation rule (both sides)
Strip trailing blanks from message texts (`WS-MESSAGE X(80)`, `CSMSG01Y` `X(50)`, table names `X(35)`); everything else byte-exact.
Menu labels: `nn. ` + name, trailing blanks stripped (`cbl:269-272` STRING DELIMITED BY SIZE puts the padded X(35) name into X(40)).

## Derivation of the option edit (`cbl:117-134`)
```
scan OPTIONI X(2) from the right for last non-blank -> WS-IDX (min 1)     :117-121
MOVE OPTIONI(1:WS-IDX) TO WS-OPTION-X  X(02) JUST RIGHT                 :122
INSPECT WS-OPTION-X REPLACING ALL ' ' BY '0'                             :123
MOVE WS-OPTION-X TO WS-OPTION 9(02) ; MOVE WS-OPTION TO OPTIONO (echo)  :124-125
E-08 if NOT NUMERIC or > 11 or = 0 : 'Please enter a valid option number...'  :127-133
```
| typed | WS-IDX | WS-OPTION-X | echo | class |
|---|---|---|---|---|
| `  ` | 1 (loop floor) | `OPTIONI(1:1)`=`' '` JUST RIGHT into X(2) -> `'  '` -> `00` | `00` | ZEROS -> E-08 |
| `0`/`00` | 1/2 | `' 0'`->`00` / `00` | `00` | ZEROS -> E-08 |
| `1` (` 1`, `01`, `1 `) | 1 (2,2,1) | ` 1`->`01`, ` 1`->`01`, `01`, `01` | `01` | valid -> dispatch COACTVWC |
| `2`..`11` | | `02`..`11` | `02`..`11` | valid -> dispatch (excluded programs) |
| `12`..`99` | 2 | as typed | as typed | > 11 -> E-08 |
| `A` | 1 | ` A`->`0A` | `0A` | NOT NUMERIC -> E-08 |
| `1A`,`A1`,`?` | 2,2,1 | `1A`,`A1`,`0?` | same | NOT NUMERIC -> E-08 |

Note (BMS): `OPTION` is `ATTRB=(...,NUM,UNPROT)`, `JUSTIFY=(RIGHT,ZERO)` (`bms:145-149`): a real 3270 right-justifies and
zero-fills the field itself and (with numeric lock) refuses letters. The COBOL edit is therefore redundant on a terminal; the
target must implement the COBOL edit because an HTTP client has no 3270 in front of it.

## Ruling input — guard after E-08 (`cbl:127-143`, item 3 of the brief)
`SEND-MENU-SCREEN` does not end the task: after E-08 the program falls through to `IF CDEMO-USRTYP-USER AND
CDEMO-MENU-OPT-USRTYPE(WS-OPTION) = 'A'` (`:136-137`) with the failing subscript.
Table geometry (`tbl:23-98`): 11 entries x 46 bytes = 506 bytes of VALUE data; `OCCURS 12` REDEFINES = 552 bytes (row 12 lies
past the 01 item). `USRTYPE(k)` is at offset `(k-1)*46 + 45`.
- `k = 0` (blank/`0`/`00`): offset -1 = last byte of `CDEMO-MENU-OPT-COUNT PIC 9(02) VALUE 11` = `'1'` -> guard FALSE. **Deterministic:** legacy screen = E-08, echo `00`. FACT.
- non-numeric (`0A`,`1A`,`A1`,`0?`): the subscript is taken from the zoned digits (`x'C1'`->1 ...) -> some k in 0..99 (INFERRED, compiler-dependent), never an abend without SSRANGE; if k in 1..11 the byte is `'U'` -> guard FALSE.
- `k = 12`: byte 551 lies past `CARDDEMO-MAIN-MENU-OPTIONS` (in `COMEN1AI` or alignment slack) -> content undefined; `'A'` extremely unlikely. INFERRED.
- `k = 13..99`: offset up to 4553 past the table start = still inside this program's WORKING-STORAGE (map ~740 bytes + `COTTL01Y`, `CSDAT01Y`, `CSMSG01Y`, `CSUSR01Y`, `DFHAID`, `DFHBMSCA` ...) -> no protection exception under IBM default `NOSSRANGE`; with `SSRANGE` an IGZ0006S/abend after the E-08 SEND. INFERRED.
- If the garbage byte were `'A'` for a `U` user, a **second** `SEND MAP ... ERASE` with `'No access - Admin Only option... '` would overwrite E-08 (`:138-142`). Otherwise `ERR-FLG-ON` is already set by `:130`, so `:145-191` is skipped and the task returns with E-08 on the screen.

Expected legacy observable for 12..99: **E-08 with echo as typed** (FACT for the first SEND; the final screen is E-08 unless the
storage/compile conditions above bite). The FR already declares the non-reproduction as **V-3 / R-07** (FR §5) and §7 row
"option 12..99". It is NOT listed in DV-01..05 of the plan.

## Case table
| Case | Req / AC | Input | Seed | Expected (source) |
|---|---|---|---|---|
| M-01 | MEN-01 / FR-09, FR-05(MEN) / AC-MEN-01 | authenticated `U` -> `GET /api/menu`; `/menu` rendered | fixture USER0001 | header `CM00` (`cbl:37`), `COMEN01C` (`:36`), titles `AWS Mainframe Modernization` / `CardDemo` (`COTTL01Y`, trimmed), date `mm/dd/yy` (`:247-251`), time `hh:mm:ss` (`:253-257`); 11 labels `01. Account View` .. `11. Pending Authorization View` (`:262-303`, `tbl:25-90`); row 12 blank/absent (`tbl:21`); footer `ENTER=Continue  F3=Exit` (`bms:162`); `Main Menu` (`bms:79`); prompt `Please select an option :` (`bms:144`); option empty (`:89` LOW-VALUES), ERRMSG blank (`:79-80`); no banner |
| M-02 | route table / B-0033 / AC-MEN-02 | `GET /api/menu` rows | — | all 11 `(number,name,program,userType)` rows equal `tbl:25-90`; only row 1 `implemented` (S-01 scope), rows 2..11 disabled |
| M-03 | MEN-02 / FR-10 / AC-MEN-03 | `POST /api/menu/select {option:''}`, `'  '`, missing | — | 400 `Please enter a valid option number...`, echo `00` (`:117-133`); UI shows `00` |
| M-04 | MEN-02 / FR-10 / AC-MEN-04 | `'0'`, `'00'`, `'12'`, `'99'`, `'A'`, `'1A'`, `'A1'`, `'?'`, `' A'` | — | 400 E-08; echoes `00`,`00`,`12`,`99`,`0A`,`1A`,`A1`,`0?`,`0A` |
| M-05 | V-3 / R-07 / AC-MEN-05 (item 3 ruling) | `'12'`, `'99'` | — | 400 E-08, echo as typed; no 500/exception (legacy: see ruling above) |
| M-06 | MEN-03 / FR-11(MEN) / AC-MEN-06 | `'1'`, `' 1'`, `'01'`, `'1 '` | — | 200 `{program:'COACTVWC', endpoint:'/api/accounts/{acctId}', implemented:true}` (`:117-125`, `:177-187`, `tbl:25-29`); SPA -> `/accounts/view` |
| M-07 | MEN-06 / Q-12, B-0032 / AC-MEN-07 | `'2'`..`'11'` (also `'02'`,`' 2'`) | — | 200 `{implemented:false, message:'Option not available in this release'}` (NEW TEXT, plan Q-12), `program` = `tbl` PGMNAME of that row; legacy XCTL/INQUIRE (`:147-187`) excluded; no `is not installed`/`coming soon` text; SPA stays `/menu` |
| M-08 | V-4 admin guard (dead) | `U` session, any option | — | `'No access - Admin Only option...'` never produced (all rows `U`, `tbl:29..89`); assert absence in responses |
| M-09 | MEN-04 / FR-12 / AC-MEN-08 | Exit button / F3 / Esc; `POST /api/auth/signoff` | session | PF3 = `XCTL COSGN00C` **without COMMAREA** (`:196-203`) -> `COSGN00C.cbl:80-83` `EIBCALEN = 0` -> blank sign-on map; the thank-you text is only produced when COSGN00C itself receives PF3 (`COSGN00C.cbl:88-89`), so **no** thank-you text is expected on the screen after a menu Exit; session invalid (`:96-98`, `:196-203` XCTL without COMMAREA); next `GET /api/menu` -> 401; SPA at `/signon` |
| M-10 | MEN-05 / FR-25(MEN) / DV-03 / AC-MEN-09 | F1/F7/F12/PageDown/F5 on `/menu` | — | legacy E-03 `Invalid key pressed. Please see below...` (`:99-102`, `CSMSG01Y:20-21`); target DV-03: no message, no request, literal absent |
| M-11 | Q-01 / AC-MEN-10 | `A` session (ADMIN001) -> `GET /api/menu`, `/menu` | fixture | same 11 rows; banner `Administration is not available in this release` (NEW TEXT); option 1 -> `implemented:true` as for `U`; legacy would be in `COADM01C` (never here) |
| M-12 | V-7 / B-0027 / AC-MEN-11 | no cookie -> `GET /api/menu`, `POST /api/menu/select` | — | JSON 401 (`:82-84` EIBCALEN=0 -> sign-on); SPA route guard -> `/signon` |
| M-13 | B-0027 / DV-02 / AC-MEN-12 | USER0001: menu -> option 1 -> `/accounts/view` -> Exit -> menu | fixture | `GET /api/auth/session` still `USER0001`/`U` (`:181-182` commented => identity pass-through) |
| M-14 | D-0016 / AC-MEN-13 | all responses/URLs | — | `CM00` only in header text, never a route |
| M-15 | HTTP surface / Q-14 | malformed JSON, option length 3 (`'123'`), non-string option | — | legacy impossible (X(2) field); target must reject deterministically (400), never 500; documented target-only |
| M-16 | D-0040 class / read path | PostgreSQL stopped during `GET /api/menu`, `POST /api/menu/select`, `GET /api/auth/session` | session | program has **no data access** (`cbl` §6 of FR: only `:39`/`:57` unused). Expect the menu to keep working from the session (informational); any 5xx must be the mapped text, not a stack trace |
| M-17 | field map / B-0029 | Angular `/menu` DOM vs `bms:29-162` | — | see field-map table in results |
| M-18 | per-option click ≙ typed number (UI, Q-12) | click row 1 / row 2 | — | click row n fills the option field with `nn` and submits; row 2..11 disabled |
| M-19 | UI option input | `maxlength=2`; typed `1` echo `01`; typed `A` echo `0A` (A-MEN-1) | — | value after ENTER = normalised echo |

## GnuCOBOL differential (norm_oracle.cbl / norm_oracle.out) - dialect divergence, not a target defect
`norm_oracle.cbl` replays `:117-133` with the same PICs. GnuCOBOL (`-std=default`, `ibm`, `ibm-strict`, `mf` identical) converts the
alphanumeric -> `PIC 9(02)` MOVE at `:124` digit-by-digit, so `'A '`/`'A1'`/`'? '`/`' A'` echo `00` and `'1A'` echoes `01` and is
**accepted as option 1**. IBM Enterprise COBOL moves the bytes of an alphanumeric sender into a DISPLAY numeric receiver unchanged
(no conversion, no validation); `:125` then moves those same bytes to `OPTIONO PIC X(2)` and `:127 IS NUMERIC` is FALSE. The
expected values in the case table therefore follow the IBM (production) semantics: echo `0A`, `1A`, `A1`, `0?`, `0A` with E-08.
Confidence MEDIUM (dialect rule, cannot be executed off-host: `07_runbook.md`, CICS programs do not compile). The BMS field is
`ATTRB=(...,NUM,...)` (`bms:145`), so on a real 3270 the keyboard numeric lock makes non-numeric input exceptional in the first
place; the derived non-numeric cases are edge cases, not a business path. The GnuCOBOL result is recorded as a second oracle that
DISAGREES with the derivation and was resolved in favour of the derivation (playbook step 7); it must not be used to approve `1A`
dispatching to option 1.
