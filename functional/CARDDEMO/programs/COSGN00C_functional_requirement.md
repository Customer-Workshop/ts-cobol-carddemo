# COSGN00C — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: COSGN00C — `app/cbl/COSGN00C.cbl`. Stream S-01, **wave 2** (consumes wave-1 seams).
- Role: entry/validator — sign-on screen, credential authentication, role-based routing. Shared shell program: entry point for the entire online module (ported once, owner S-01).

## 2. Trigger / caller contract
- CICS transaction `CC00` (`app/csd/CARDDEMO.CSD:378`); terminal-initiated, `EIBCALEN = 0` on first entry (`COSGN00C.cbl:71-74`); re-entered pseudo-conversationally with its own COMMAREA (`:98-102`).
- On success it delegates with `CARDDEMO-COMMAREA` (`app/cpy/COCOM01Y.cpy:19-31`) populated: FROM-TRANID='CC00', FROM-PROGRAM='COSGN00C', USER-ID, USER-TYPE, PGM-CONTEXT=0 (`:225-229`).

## 3. Inputs and outputs
Inputs (map COSGN0A / mapset COSGN00, `app/cpy-bms/COSGN00.CPY:67-78`): USERIDI X(8) (dict: users.user_id), PASSWDI X(8) (dark field, dict: password). AID key (EIBAID).
Reads: USRSEC KSDS record SEC-USER-DATA (`app/cpy/CSUSR01Y.cpy:17-23`), key = upper-cased user id (`:211-219`).
Outputs: ERRMSGO X(80); header fields TRNNAME/PGMNAME/CURDATE/CURTIME/APPLID/SYSID (`:177-204`); plain farewell text on PF3 (`:88-90`, `:301-311`); outgoing COMMAREA on XCTL.

## 4. Functional requirements owned (all cross-ref stream FR)
| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| COSGN00C-01 | blank User ID | `Please enter User ID ...`, cursor USERID | :118-122 | FR-S01-01 |
| COSGN00C-02 | blank Password | `Please enter Password ...`, cursor PASSWD | :123-127 | FR-S01-02 |
| COSGN00C-03 | user not found (RESP 13) | `User not found. Try again ...` | :247-251 | FR-S01-03 |
| COSGN00C-04 | password mismatch | `Wrong Password. Try again ...` | :241-246 | FR-S01-04 |
| COSGN00C-05 | valid admin credentials | route to admin menu with populated context | :225-234 | FR-S01-05 |
| COSGN00C-06 | valid regular credentials | route to main menu with populated context | :225-240 | FR-S01-06 |
| COSGN00C-07 | security store error (other RESP) | `Unable to verify the User ...` | :252-256 | FR-S01-07 |
| COSGN00C-08 | PF3 | farewell text, session end | :88-90 | FR-S01-08 |
| COSGN00C-09 | lower-case entry | credentials upper-cased before auth | :132-136 | FR-S01-09 |
| COSGN00C-10 | unmapped AID | `Invalid key pressed...`, redisplay | :91-94 | FR-S01-20 |

## 5. Business rules and validations
Sequence (blocking, first failure wins): user id mandatory → password mandatory → uppercase both → keyed read → RESP protocol (0 compare / 13 not-found / other error) → password compare → role route (`:112-257`). All blocking; no warnings.

## 6. Data access and boundaries
- USRSEC read-only keyed read (S01-B4, **DECIDED**: Postgres `users` + `IUserSecurityRepository`, no SP; store error → COSGN00C-07 result). No writes, no commit scope.
- Session contract (S01-B6, DECIDED: `SessionContext`/JWT claims).
- Outbound routing to menus is intra-stream (wave-3 targets), not a register boundary.
- **Deviation (approved at STOP C)**: target stores hashed passwords; comparison via `PasswordHasher`, outcomes identical to `:223`.

## 7. Error and edge behavior
Empty/low-value screen fields treated as blank (`:118,123`); map RECEIVE RESP captured but unchecked (`:167-175`, technical); wrong password clears the password field and redisplays (`:243-245`).

## 8. Hard-stop boundary
Delegates everything past authentication: menu rendering/dispatch (COMEN01C/COADM01C). Not responsible for any menu behavior.

## 9. Demoted mechanics
Pseudo-conversational RETURN TRANSID (`:98-102`); SEND-SIGNON-SCREEN plumbing (`:263-276`); header date/time formatting (`:177-196`); APPLID/SYSID via ASSIGN (`:198-204` → target app config).

## 10. Traceability
COSGN00C-01..10 ↔ FR-S01-01..09,20 (table §4) ↔ parity tests (wave 2, `!mf_program_parity_test`).
