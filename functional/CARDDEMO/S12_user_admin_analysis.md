# S-12 User Admin — Stream Analysis (`!mf_stream_analysis`)

Status: complete (2026-09-02). Process type **ONLINE**. Stream catalogue row: `functional/CARDDEMO/CardDemo_inventory.md` §5 (`S-12 | User Admin | ONLINE | CU00–CU03 | COUSR00C, COUSR01C, COUSR02C, COUSR03C`).
Target profiles applied (read-only): CORE + ONLINE + DATA/BOUNDARY from `functional/CARDDEMO/CardDemo_target_state.md`. Built on the S-01 shell (JWT session, admin menu, route registry) and the shared data layer landed at commit `468e17d` (`users` table already present from S-01 wave 1).

## 1. Pinned stream

- **Entry points (proof)**: `CU00 -> COUSR00C`, `CU01 -> COUSR01C`, `CU02 -> COUSR02C`, `CU03 -> COUSR03C` (`app/csd/CARDDEMO.CSD:449`, `:459`, `:469`, `:479`). All four are reached from the admin menu COADM01C (`app/cpy/COADM02Y.cpy` options 01–04) — the admin menu is the only caller, so the stream is admin-gated by the shell, not by the programs themselves.
- **Hard stop**: every `XCTL` leaving the four programs is either (a) the return to the caller `COADM01C` / `CDEMO-FROM-PROGRAM` (S-01 owned, already migrated), (b) the bounce to `COSGN00C` when `EIBCALEN = 0` (S-01 owned), or (c) within-stream navigation `COUSR00C -> COUSR02C/COUSR03C`. Nothing crosses into another stream.
- **Exclusions**: none of the other admin options (COTRTLIC/COTRTUPC, S-21) are touched; the menu route registry flags for options 01–04 stay `Enabled: false` (integration stage flips them).
- Pseudo-conversational shape: every program ends `EXEC CICS RETURN TRANSID(WS-TRANID) COMMAREA(CARDDEMO-COMMAREA)` (`COUSR00C.cbl:141-144`, `COUSR01C.cbl:107-110`, `COUSR02C.cbl:135-138`, `COUSR03C.cbl:134-137`) and re-enters on `CDEMO-PGM-REENTER` (`COCOM01Y.cpy`).

## 2. Program inventory + leaf-first DAG

| Program | Path | Role | Callees / edges | Shared? | Present |
|---|---|---|---|---|---|
| COUSR00C | app/cbl/COUSR00C.cbl | list/browse (user list, 10 rows/page) | STARTBR/READNEXT/READPREV/ENDBR USRSEC (:588-681); XCTL COUSR02C (:192-199) / COUSR03C (:202-209); XCTL COADM01C on PF3 (:126-127) | stream entry | yes |
| COUSR01C | app/cbl/COUSR01C.cbl | create (user add) | WRITE USRSEC (:240-248); XCTL COADM01C on PF3 (:94-95) | leaf | yes |
| COUSR02C | app/cbl/COUSR02C.cbl | read + update (user update) | READ UPDATE USRSEC (:322-331); REWRITE USRSEC (:360-366); XCTL FROM-PROGRAM/COADM01C (:111-119, :124-126) | leaf, called by COUSR00C | yes |
| COUSR03C | app/cbl/COUSR03C.cbl | read + delete (user delete) | READ UPDATE USRSEC (:269-278); DELETE USRSEC (:307-312); XCTL FROM-PROGRAM/COADM01C (:111-118, :123-125) | leaf, called by COUSR00C | yes |

No subroutine `CALL`s. Shared copybooks: `CSUSR01Y.cpy` (USRSEC record), `COCOM01Y.cpy` (COMMAREA), `CSMSG01Y.cpy` (invalid-key message), `COTTL01Y.cpy`/`CSDAT01Y.cpy` (header titles/dates — demoted mechanics).

**Leaf-first DAG**: COUSR01C, COUSR02C, COUSR03C are leaves (depth 0); COUSR00C (depth 1) dispatches to COUSR02C/COUSR03C. All four return to the S-01 shell (COADM01C).

```
COADM01C (S-01) ──► COUSR00C ──U──► COUSR02C ──PF3──► caller (COUSR00C or COADM01C)
      │                 └────D──► COUSR03C ──PF3──► caller
      ├──► COUSR01C ──PF3──► COADM01C
      ├──► COUSR02C ──PF12──► COADM01C
      └──► COUSR03C ──PF12──► COADM01C
```

## 3. Surfaces (ONLINE)

### COUSR00C — screen COUSR0A / mapset COUSR00 (`app/bms/COUSR00.bms`)
- Header: TRNNAME X(4), TITLE01/02 X(40), PGMNAME X(8), CURDATE X(8), CURTIME X(8) (rows 1–2, demoted).
- `PAGENUM` X(8) ASKIP at (4,71) — page number (`:85-88`).
- `USRIDIN` X(8) UNPROT at (6,...) — search key "Search User ID" (`:95`).
- Instruction `Type 'U' to Update or 'D' to Delete a User from the list`.
- 10 detail rows (rows 9–18): `SELnnnn` X(1) UNPROT, `USRIDnn` X(8), `FNAMEnn` X(20), `LNAMEnn` X(20), `UTYPEnn` X(1) (all ASKIP).
- `ERRMSG` X(78) BRT RED at (23,1) (`:449-452`).
- Footer `ENTER=Continue  F3=Back  F7=Backward  F8=Forward`.

### COUSR01C — screen COUSR1A / mapset COUSR01 (`app/bms/COUSR01.bms`)
- `FNAME` X(20) UNPROT, `LNAME` X(20) UNPROT (row 8); `USERID` X(8) UNPROT, `PASSWD` X(8) UNPROT **DRK** (row 11); `USRTYPE` X(1) UNPROT (row 14) with hint `(A=Admin, U=User)`.
- `ERRMSG` X(78) at (23,1). Footer `ENTER=Add User  F3=Back  F4=Clear  F12=Exit` (F12 is footer text only — COUSR01C handles ENTER/PF3/PF4 and treats PF12 as invalid key, `COUSR01C.cbl:90-103`).
- Initial cursor on FNAME (`COUSR01C.cbl:86, 289`).

### COUSR02C — screen COUSR2A / mapset COUSR02 (`app/bms/COUSR02.bms`)
- `USRIDIN` X(8) UNPROT (search/fetch key); `FNAME` X(20), `LNAME` X(20), `PASSWD` X(8) **DRK** UNPROT (`:130`), `USRTYPE` X(1) UNPROT.
- `ERRMSG` X(78). Footer `ENTER=Fetch  F3=Save&Exit  F4=Clear  F5=Save  F12=Cancel`.
- Initial cursor on USRIDIN (`COUSR02C.cbl:405`).

### COUSR03C — screen COUSR3A / mapset COUSR03 (`app/bms/COUSR03.bms`)
- `USRIDIN` X(8) UNPROT; `FNAME` X(20), `LNAME` X(20), `USRTYPE` X(1) displayed read-only after fetch (program moves values, no password shown).
- `ERRMSG` X(78). Footer `ENTER=Fetch  F3=Back  F4=Clear  F5=Delete` (PF12 also handled in code, `COUSR03C.cbl:123-125`).

## 4. Data + field dictionary

USRSEC KSDS `AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS`, `KEYS(8,0)`, `RECORDSIZE(80,80)` (`app/jcl/DUSRSECJ.jcl`). Record `SEC-USER-DATA` (`app/cpy/CSUSR01Y.cpy`):

| COBOL field | PIC | Target (`users`, S-01 wave 1) | Notes |
|---|---|---|---|
| SEC-USR-ID | X(08) | `user_id` varchar(8) PK | browse key; case preserved as typed (no UPPER-CASE in any COUSR program) |
| SEC-USR-FNAME | X(20) | `first_name` varchar(20) | |
| SEC-USR-LNAME | X(20) | `last_name` varchar(20) | |
| SEC-USR-PWD | X(08) | `password_hash` text | **hashed** in target (approved S-01 storage deviation, STOP C) |
| SEC-USR-TYPE | X(01) | `user_type` char(1) via `UserType` enum ('A'/'U') | source accepts any non-blank char (S12-B1) |
| SEC-USR-FILLER | X(23) | not mapped | |

COMMAREA extensions used (`COCOM01Y.cpy` + program-local `CDEMO-CU0n-INFO`): `CDEMO-CU00-USRID-FIRST/LAST` X(8), `CDEMO-CU00-PAGE-NUM` 9(8), `CDEMO-CU00-NEXT-PAGE-FLG` Y/N, `CDEMO-CU00-USR-SEL-FLG` X(1), `CDEMO-CU00-USR-SELECTED` X(8) (`COUSR00C.cbl:67-75`); COUSR02C/03C read `CDEMO-CU02-USR-SELECTED` / `CDEMO-CU03-USR-SELECTED` at the same offsets (`COUSR02C.cbl:51-58`, `COUSR03C.cbl:51-58`) — the selected user id travels from the list to update/delete.

No new tables, columns, or indexes are needed: the `users` PK supports keyed browse in both directions.

## 5. Boundary table (headline)

| ID | Boundary | Where | Decision (see migration plan §3) |
|---|---|---|---|
| S12-B1 | User type domain: source writes any non-blank X(1) (`COUSR01C.cbl:142-147, 158`); target `UserType` enum only admits 'A'/'U' | COUSR01C, COUSR02C | Keep shared enum; a code outside A/U fails the write and surfaces the source's OTHER-path message (`Unable to Add User...` / `Unable to Update User...`). Not a new validation message. |
| S12-B2 | Password compare on update: source compares clear text `PASSWDI NOT = SEC-USR-PWD` (`COUSR02C.cbl:227-230`) and echoes stored password into the (dark) field on fetch (`:168`) | COUSR02C | Target never returns the stored password; fetch returns blank password; "modified" = supplied password does not verify against the stored hash. Consequence of the approved hashing deviation. |
| S12-B3 | Stale rows after STARTBR NOTFND / short backward page: BMS input/output overlay leaves previous rows on screen (`COUSR00C.cbl:292-296, 346-350`) | COUSR00C | Target renders exactly the rows returned (empty / partial list) with the same message and page number. Display-only artifact, not business behaviour. |
| S12-B4 | Caller return: PF3 on COUSR02C/03C returns to `CDEMO-FROM-PROGRAM` (COUSR00C or COADM01C) | COUSR02C, COUSR03C | Frontend carries `from` in the route query string; backend has no navigation state. |
| S12-B5 | Admin gate: programs do not check user type; only the admin menu (S-01) reaches CU00–CU03 | all | API requires JWT `userType='A'` (403 otherwise, same idiom as `/api/v1/menu?menu=admin`); routes use `adminGuard`. |

## 6. Waves (leaf-first)

1. **Wave 1 — COUSR01C** (add): repository `AddAsync` + duplicate detection; `POST /api/v1/admin/users/add`; Angular `user-add` screen.
2. **Wave 2 — COUSR02C, COUSR03C** (update/delete leaves): `UpdateAsync`, `DeleteAsync`; fetch/update/delete commands; Angular `user-update`, `user-delete` screens.
3. **Wave 3 — COUSR00C** (list): `BrowseForwardAsync`/`BrowseBackwardAsync` on the users repository; `POST /api/v1/admin/users/list`; Angular `user-list` screen with row selection → update/delete routes.

All three waves ship together in this stream branch (single child session); the order above is the implementation order.

## 7. Risks

- Message text reuse: COUSR03C's DELETE OTHER path says `Unable to Update User...` (`COUSR03C.cbl:332`) — preserved verbatim (parity rule), flagged in the FR doc.
- Paging semantics depend on key order of `user_id` (ordinal string compare) — Postgres collation must be byte/ordinal for parity with VSAM; the browse queries use `string.CompareTo` translated by Npgsql to text comparison under the database collation. Sample data is upper-case ASCII so ordering is identical; documented in the plan.
- PF4 clear and PF12 cancel are frontend-only behaviours (no I/O) — covered by component specs.

## 8. Validation

- COBOL compile check: `cobc -I app/cpy -fsign=EBCDIC -x app/cbl/COUSR0nC.cbl` (CICS statements are not compilable under GnuCOBOL without a preprocessor; the check is documentary only, as for S-01).
- Every FR in `S12_functional_requirement.md` has a backend unit test (in-memory repository), an integration test (Testcontainers PostgreSQL) where I/O is involved, and a frontend spec where the behaviour is UI-owned (PF keys, clear, navigation).
