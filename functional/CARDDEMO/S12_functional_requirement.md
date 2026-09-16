# S-12 User Admin — Stream Functional Requirements (`!mf_stream_fr_generation`)

Status: complete (2026-09-02). Source of truth: `app/cbl/COUSR00C.cbl`, `COUSR01C.cbl`, `COUSR02C.cbl`, `COUSR03C.cbl`; maps `app/bms/COUSR00.bms`..`COUSR03.bms`; record `app/cpy/CSUSR01Y.cpy`; COMMAREA `app/cpy/COCOM01Y.cpy`; shared message `app/cpy/CSMSG01Y.cpy`. Line cites are `<PGM>.cbl:<from>-<to>` (bare `:n` = same program as the row).

## 1. Purpose and scope
Administrative maintenance of the USRSEC security file: list/browse users (COUSR00C), add a user (COUSR01C), update a user (COUSR02C), delete a user (COUSR03C). In scope: the four programs, their screens, validations, messages, file access and PF-key behaviour. Out of scope: the admin menu that reaches them (S-01), sign-on (S-01), any other admin option.

## 2. Actors and preconditions
- Actor: an administrator signed on through COSGN00C with `SEC-USR-TYPE = 'A'` and routed via COADM01C (options 01–04). The programs themselves do not check the user type; the target enforces the admin gate at the API and route level (S12-B5).
- Precondition for every program: a populated COMMAREA. `EIBCALEN = 0` bounces to COSGN00C (`COUSR00C.cbl:110-112`, `COUSR01C.cbl:78-80`, `COUSR02C.cbl:90-92`, `COUSR03C.cbl:90-92`) — in the target this is the `authGuard` redirect to `/signin`.

## 3. Surface specification
### User list COUSR0A (`app/bms/COUSR00.bms`)
| Field | Len | Attr | Meaning |
|---|---|---|---|
| PAGENUM | 8 | ASKIP | page number (`CDEMO-CU00-PAGE-NUM`) |
| USRIDIN | 8 | UNPROT | search user id (browse start key) |
| SEL0001..SEL0010 | 1 | UNPROT | row selection (`U`/`D`) |
| USRID01..10 / FNAME01..10 / LNAME01..10 / UTYPE01..10 | 8 / 20 / 20 / 1 | ASKIP | row data |
| ERRMSG | 78 | BRT RED | message area |
Footer: `ENTER=Continue  F3=Back  F7=Backward  F8=Forward`. Instruction: `Type 'U' to Update or 'D' to Delete a User from the list`.

### User add COUSR1A (`app/bms/COUSR01.bms`)
FNAME X(20), LNAME X(20), USERID X(8), PASSWD X(8) DRK, USRTYPE X(1) `(A=Admin, U=User)`, ERRMSG X(78). Footer: `ENTER=Add User  F3=Back  F4=Clear  F12=Exit`. Initial cursor FNAME.

### User update COUSR2A (`app/bms/COUSR02.bms`)
USRIDIN X(8), FNAME X(20), LNAME X(20), PASSWD X(8) DRK, USRTYPE X(1), ERRMSG X(78). Footer: `ENTER=Fetch  F3=Save&Exit  F4=Clear  F5=Save  F12=Cancel`. Initial cursor USRIDIN.

### User delete COUSR3A (`app/bms/COUSR03.bms`)
USRIDIN X(8), FNAME X(20), LNAME X(20), USRTYPE X(1) (display), ERRMSG X(78). Footer: `ENTER=Fetch  F3=Back  F4=Clear  F5=Delete`. Initial cursor USRIDIN.

## 4. Functional requirements (KEEP)

| ID | Area | Trigger | Observable result | Program | Cite | Boundary | Test |
|---|---|---|---|---|---|---|---|
| FR-S12-01 | List | first entry (no re-enter flag) | first 10 users in key order from low-values, page 1, `NEXT-PAGE` flag from look-ahead read | COUSR00C | :115-119, :216-228, :282-331 | — | unit+int |
| FR-S12-02 | List | ENTER with USRIDIN non-blank and no row selected | list restarts at the first key ≥ USRIDIN, page number reset to 1 | COUSR00C | :218-228 | — | unit+int |
| FR-S12-03 | List | ENTER, first non-blank SEL row = `U`/`u` | transfer to COUSR02C with the row's user id (`CDEMO-CU00-USR-SELECTED`), FROM-PROGRAM = COUSR00C | COUSR00C | :151-199 | S12-B4 | unit+ui |
| FR-S12-04 | List | ENTER, first non-blank SEL row = `D`/`d` | transfer to COUSR03C with the row's user id, FROM-PROGRAM = COUSR00C | COUSR00C | :200-209 | S12-B4 | unit+ui |
| FR-S12-05 | List | ENTER, first non-blank SEL row is any other char | `Invalid selection. Valid values are U and D`; list is then refreshed from the search key (page 1) | COUSR00C | :210-228 | — | unit+ui |
| FR-S12-06 | List | several SEL rows filled | only the first (lowest row) non-blank selection is honoured | COUSR00C | :151-185 | — | ui |
| FR-S12-07 | List | PF8 with `NEXT-PAGE-YES` | next 10 users after `USRID-LAST`, page number +1; `NEXT-PAGE` re-derived by look-ahead read | COUSR00C | :258-278, :282-331 | — | unit+int |
| FR-S12-08 | List | PF8 with `NEXT-PAGE-NO` | `You are already at the bottom of the page...`, page unchanged | COUSR00C | :270-277 | — | unit |
| FR-S12-09 | List | forward read reaches end of file (short page or look-ahead) | `You have reached the bottom of the page...`, `NEXT-PAGE-NO`; page number incremented only if at least one row was read | COUSR00C | :308-323, :634-641 | — | unit+int |
| FR-S12-10 | List | PF7 with page number > 1 | previous 10 users before `USRID-FIRST`, page number −1 (never below 1), `NEXT-PAGE-YES` | COUSR00C | :236-256, :336-379 | — | unit+int |
| FR-S12-11 | List | PF7 with page number ≤ 1 | `You are already at the top of the page...`, page unchanged | COUSR00C | :248-255 | — | unit |
| FR-S12-12 | List | backward read reaches start of file (look-behind or short page) | `You have reached the top of the page...`; page number becomes 1 when the look-behind hits start of file | COUSR00C | :362-372, :668-675 | S12-B3 | unit+int |
| FR-S12-13 | List | no user with key ≥ search key (STARTBR NOTFND) | `You are at the top of the page...`, no rows returned, page number 0 | COUSR00C | :600-605 | S12-B3 | unit+int |
| FR-S12-14 | List | any other file error on STARTBR/READNEXT/READPREV | `Unable to lookup User...` | COUSR00C | :607-613, :641-647, :675-681 | — | unit |
| FR-S12-15 | List | PF3 | return to COADM01C (admin menu) | COUSR00C | :125-127 | — | ui |
| FR-S12-16 | List | any other AID | `Invalid key pressed. Please see below...` (CSMSG01Y.cpy) | COUSR00C | :132-136 | — | ui |
| FR-S12-17 | Add | ENTER, validation | in order: FNAME blank → `First Name can NOT be empty...`; LNAME blank → `Last Name can NOT be empty...`; USERID blank → `User ID can NOT be empty...`; PASSWD blank → `Password can NOT be empty...`; USRTYPE blank → `User Type can NOT be empty...`; first failure wins, cursor on the failing field | COUSR01C | :118-147 | — | unit+ui |
| FR-S12-18 | Add | all five fields present, key not on file | record written keyed by USERID; fields cleared; green `User <id> has been added ...` | COUSR01C | :153-159, :240-259 | S12-B1 | unit+int |
| FR-S12-19 | Add | key already on file (DUPKEY/DUPREC) | `User ID already exist...`, fields retained | COUSR01C | :260-266 | — | unit+int |
| FR-S12-20 | Add | any other WRITE error | `Unable to Add User...` | COUSR01C | :267-273 | S12-B1 | unit |
| FR-S12-21 | Add | PF3 | return to COADM01C | COUSR01C | :93-95 | — | ui |
| FR-S12-22 | Add | PF4 | all five fields and the message cleared, cursor FNAME | COUSR01C | :96-97, :279-295 | — | ui |
| FR-S12-23 | Add | any other AID (incl. PF12 despite footer) | `Invalid key pressed. Please see below...` | COUSR01C | :98-102 | — | ui |
| FR-S12-24 | Update | entry with `CDEMO-CU02-USR-SELECTED` populated (from COUSR00C) | that user is fetched immediately as if ENTER were pressed | COUSR02C | :96-105 | S12-B4 | ui |
| FR-S12-25 | Update | ENTER with USRIDIN blank | `User ID can NOT be empty...` | COUSR02C | :146-151 | — | unit+ui |
| FR-S12-26 | Update | ENTER, user found | FNAME/LNAME/USRTYPE populated (PASSWD blank in target, S12-B2); neutral `Press PF5 key to save your updates ...` | COUSR02C | :152-172, :334-339 | S12-B2 | unit+int |
| FR-S12-27 | Update | ENTER, user not found | `User ID NOT found...` | COUSR02C | :340-345 | — | unit+int |
| FR-S12-28 | Update | READ other error | `Unable to lookup User...` | COUSR02C | :346-352 | — | unit |
| FR-S12-29 | Update | PF5/PF3 save, validation | in order: USRIDIN → `User ID can NOT be empty...`; FNAME → `First Name can NOT be empty...`; LNAME → `Last Name can NOT be empty...`; PASSWD → `Password can NOT be empty...`; USRTYPE → `User Type can NOT be empty...` | COUSR02C | :180-209 | — | unit+ui |
| FR-S12-30 | Update | save with no field different from the stored record | red `Please modify to update ...`, no write | COUSR02C | :217-243 | S12-B2 | unit+int |
| FR-S12-31 | Update | save with ≥1 field different | record rewritten; green `User <id> has been updated ...` | COUSR02C | :236-237, :360-376 | S12-B2 | unit+int |
| FR-S12-32 | Update | save, user not found / other REWRITE error | `User ID NOT found...` / `Unable to Update User...` | COUSR02C | :340-345, :377-389 | — | unit+int |
| FR-S12-33 | Update | PF3 | save attempted (FR-S12-29..32), then return to `CDEMO-FROM-PROGRAM` (COUSR00C) or COADM01C when blank — the return happens regardless of the save outcome | COUSR02C | :111-119 | S12-B4 | ui |
| FR-S12-34 | Update | PF5 | save (FR-S12-29..32) and stay on the screen | COUSR02C | :122-123 | — | ui |
| FR-S12-35 | Update | PF12 | return to COADM01C without saving | COUSR02C | :124-126 | — | ui |
| FR-S12-36 | Update | PF4 / any other AID | PF4 clears all fields and the message, cursor USRIDIN; other AID → `Invalid key pressed. Please see below...` | COUSR02C | :120-121, :127-130, :395-411 | — | ui |
| FR-S12-37 | Delete | entry with `CDEMO-CU03-USR-SELECTED` populated (from COUSR00C) | that user is fetched immediately as if ENTER were pressed | COUSR03C | :96-105 | S12-B4 | ui |
| FR-S12-38 | Delete | ENTER | USRIDIN blank → `User ID can NOT be empty...`; found → FNAME/LNAME/USRTYPE shown, neutral `Press PF5 key to delete this user ...`; not found → `User ID NOT found...`; other → `Unable to lookup User...` | COUSR03C | :145-169, :281-299 | — | unit+int+ui |
| FR-S12-39 | Delete | PF5 | USRIDIN blank → `User ID can NOT be empty...`; record deleted → fields cleared, green `User <id> has been deleted ...`; not found → `User ID NOT found...`; other DELETE error → `Unable to Update User...` (source text preserved) | COUSR03C | :177-192, :314-335 | — | unit+int+ui |
| FR-S12-40 | Delete | PF3 / PF12 / PF4 / other AID | PF3 → `CDEMO-FROM-PROGRAM` or COADM01C; PF12 → COADM01C; PF4 clears fields + message; other → `Invalid key pressed. Please see below...` | COUSR03C | :111-129, :341-356 | S12-B4 | ui |

## 5. Validation and error catalogue

| Message (exact) | Colour | Programs | Cite |
|---|---|---|---|
| `Invalid selection. Valid values are U and D` | map default (red) | COUSR00C | :211-212 |
| `You are already at the top of the page...` | red | COUSR00C | :251 |
| `You are already at the bottom of the page...` | red | COUSR00C | :273 |
| `You are at the top of the page...` | red | COUSR00C | :603 |
| `You have reached the bottom of the page...` | red | COUSR00C | :637 |
| `You have reached the top of the page...` | red | COUSR00C | :671 |
| `Unable to lookup User...` | red | COUSR00C, COUSR02C, COUSR03C | COUSR00C:610/644/678, COUSR02C:349, COUSR03C:296 |
| `First Name can NOT be empty...` | red | COUSR01C, COUSR02C | COUSR01C:120, COUSR02C:188 |
| `Last Name can NOT be empty...` | red | COUSR01C, COUSR02C | COUSR01C:126, COUSR02C:194 |
| `User ID can NOT be empty...` | red | COUSR01C, COUSR02C, COUSR03C | COUSR01C:132, COUSR02C:148/182, COUSR03C:147/179 |
| `Password can NOT be empty...` | red | COUSR01C, COUSR02C | COUSR01C:138, COUSR02C:200 |
| `User Type can NOT be empty...` | red | COUSR01C, COUSR02C | COUSR01C:144, COUSR02C:206 |
| `User <id> has been added ...` | green | COUSR01C | :252-258 |
| `User ID already exist...` | red | COUSR01C | :263 |
| `Unable to Add User...` | red | COUSR01C | :270 |
| `Press PF5 key to save your updates ...` | neutral | COUSR02C | :336-338 |
| `User ID NOT found...` | red | COUSR02C, COUSR03C | COUSR02C:342/379, COUSR03C:289/325 |
| `Please modify to update ...` | red | COUSR02C | :239-241 |
| `User <id> has been updated ...` | green | COUSR02C | :369-375 |
| `Unable to Update User...` | red | COUSR02C, COUSR03C | COUSR02C:386, COUSR03C:332 |
| `Press PF5 key to delete this user ...` | neutral | COUSR03C | :283-285 |
| `User <id> has been deleted ...` | green | COUSR03C | :315-321 |
| `Invalid key pressed. Please see below...` | red | all | CSMSG01Y.cpy |

`<id>` is `SEC-USR-ID DELIMITED BY SPACE` — the user id up to its first space.

## 6. Field and data derivations
- Browse start key: blank USRIDIN → LOW-VALUES (`COUSR00C.cbl:218-222`); PF8 start = `CDEMO-CU00-USRID-LAST`, skipping that record (`:262-266, :288-290`); PF7 start = `CDEMO-CU00-USRID-FIRST`, skipping that record (`:239-243, :342-344`). Blank USRID-LAST on PF8 → HIGH-VALUES (`:263`).
- Page number: 0 before the first forward read; +1 per successful forward page (`:309-310, :319-322`); −1 per backward page when a look-behind record exists, else 1 (`:364-371`).
- `USRID-FIRST` = row 1 user id (`:388-389`), `USRID-LAST` = row 10 user id (`:434-435`) (POPULATE-USER-DATA).
- Update "modified" test compares each of FNAME/LNAME/PASSWD/USRTYPE with the stored record (`COUSR02C.cbl:219-234`); USERID itself is not updatable (key).
- No upper-casing or trimming beyond BMS padding in any of the four programs.

## 7. Mechanics (demoted, cited)
Header population (`POPULATE-HEADER-INFO`), map SEND/RECEIVE with ERASE/CURSOR, `EXEC CICS RETURN TRANSID`, `DISPLAY 'RESP:'` diagnostics, COMMAREA copy in/out, ENDBR, `-1` cursor-length moves. Not requirements; realised by the HTTP/JSON transport and Angular focus handling.

## 8. Acceptance criteria (Given/When/Then) — one per FR
- FR-S12-01: Given ≥11 users, When the list is entered, Then users 1–10 in key order, page 1, next-page available.
- FR-S12-02: Given a search key `M`, When ENTER, Then the first row is the first user id ≥ `M` and page = 1.
- FR-S12-03/04: Given a row selected with `u`/`D`, When ENTER, Then the update/delete screen opens for that user with return-to-list context.
- FR-S12-05: Given a row selected with `X`, When ENTER, Then `Invalid selection. Valid values are U and D` and the list is re-read from the search key.
- FR-S12-06: Given rows 2 and 5 both selected, When ENTER, Then row 2's selection is used.
- FR-S12-07: Given page 1 with next-page available, When PF8, Then the next 10 users after row 10, page 2.
- FR-S12-08: Given the last page, When PF8, Then `You are already at the bottom of the page...`.
- FR-S12-09: Given 12 users on page 2, When the page holds 2 rows, Then `You have reached the bottom of the page...` and next-page is off.
- FR-S12-10: Given page 2, When PF7, Then the 10 users before row 1, page 1.
- FR-S12-11: Given page 1, When PF7, Then `You are already at the top of the page...`.
- FR-S12-12: Given page 2 reached from the first 10 users, When PF7, Then `You have reached the top of the page...` and page = 1.
- FR-S12-13: Given a search key beyond every user id, When ENTER, Then `You are at the top of the page...` and no rows.
- FR-S12-14: Given the store unavailable, When any list read, Then `Unable to lookup User...`.
- FR-S12-15/16: Given the list, When PF3 / F5, Then admin menu / `Invalid key pressed. Please see below...`.
- FR-S12-17: Given LNAME blank and all others filled, When ENTER, Then `Last Name can NOT be empty...` (and likewise for each field in the stated order).
- FR-S12-18: Given a new id, When ENTER, Then the user exists in the store and `User <id> has been added ...` with the form cleared.
- FR-S12-19: Given an existing id, When ENTER, Then `User ID already exist...`.
- FR-S12-20: Given the store rejecting the write, When ENTER, Then `Unable to Add User...`.
- FR-S12-21/22/23: Given the add screen, When PF3 / PF4 / PF12, Then admin menu / cleared form / invalid-key message.
- FR-S12-24/37: Given navigation from the list with a selected id, When the update/delete screen opens, Then the user is already fetched.
- FR-S12-25/38: Given blank USRIDIN, When ENTER, Then `User ID can NOT be empty...`.
- FR-S12-26/38: Given an existing id, When ENTER, Then names/type shown and the PF5 prompt message.
- FR-S12-27/38: Given an unknown id, When ENTER, Then `User ID NOT found...`.
- FR-S12-28: Given the store unavailable, When ENTER, Then `Unable to lookup User...`.
- FR-S12-29: Given fetched user with PASSWD blank, When PF5, Then `Password can NOT be empty...`.
- FR-S12-30: Given fetched user, same names/type and the current password, When PF5, Then `Please modify to update ...` and no write.
- FR-S12-31: Given a changed last name, When PF5, Then the store reflects it and `User <id> has been updated ...`.
- FR-S12-32: Given the user deleted meanwhile, When PF5, Then `User ID NOT found...`.
- FR-S12-33/34/35/36: Given the update screen, When PF3 / PF5 / PF12 / PF4 / F6, Then save+return / save+stay / return without save / cleared form / invalid-key message.
- FR-S12-39: Given a fetched user, When PF5, Then the user is gone from the store and `User <id> has been deleted ...` with the form cleared.
- FR-S12-40: Given the delete screen, When PF3 / PF12 / PF4 / F6, Then caller / admin menu / cleared form / invalid-key message.

## 9. Traceability matrix
FR-S12-01..16 → COUSR00C → `UserAdminService.ListAsync` (+ `UserListComponent`) → `UserAdminServiceTests` / `UserAdminIntegrationTests` / `user-list.component.spec.ts`.
FR-S12-17..23 → COUSR01C → `UserAdminService.AddAsync` (+ `UserAddComponent`) → same test files / `user-add.component.spec.ts`.
FR-S12-24..36 → COUSR02C → `UserAdminService.FetchForUpdateAsync` / `UpdateAsync` (+ `UserUpdateComponent`) → `user-update.component.spec.ts`.
FR-S12-37..40 → COUSR03C → `UserAdminService.FetchForDeleteAsync` / `DeleteAsync` (+ `UserDeleteComponent`) → `user-delete.component.spec.ts`.
API surface: `POST /api/v1/admin/users/list|add|update/fetch|update|delete/fetch|delete` (`UserAdminController`), admin-only (403 otherwise) — `UserAdminApiIntegrationTests`.

## 10. Program index
| Program | Role | FRs | Program FR doc |
|---|---|---|---|
| COUSR00C | user list / browse / select | FR-S12-01..16 | [programs/COUSR00C_functional_requirement.md](programs/COUSR00C_functional_requirement.md) |
| COUSR01C | user add | FR-S12-17..23 | [programs/COUSR01C_functional_requirement.md](programs/COUSR01C_functional_requirement.md) |
| COUSR02C | user update | FR-S12-24..36 | [programs/COUSR02C_functional_requirement.md](programs/COUSR02C_functional_requirement.md) |
| COUSR03C | user delete | FR-S12-37..40 | [programs/COUSR03C_functional_requirement.md](programs/COUSR03C_functional_requirement.md) |

## 11. Open questions and assumptions
1. **Password storage (approved S-01 deviation, S12-B2)**: passwords are hashed; the update screen cannot echo the stored password, so PASSWD is returned blank on fetch and the operator must retype it (validation FR-S12-29 already requires it). "Unchanged" is decided by hash verification. Behavioural outcomes (`Please modify to update ...` vs `has been updated`) are preserved.
2. **User type domain (S12-B1)**: the shared `UserType` enum admits only `A`/`U`. A value such as `X`, which the source would write verbatim, fails the write in the target and surfaces the source's OTHER-path message (`Unable to Add User...` / `Unable to Update User...`). No new message invented.
3. **Stale rows (S12-B3)**: the target renders exactly the rows returned; the source's stale-row overlay on NOTFND/short backward pages is a BMS artifact.
4. **COUSR03C DELETE error text** `Unable to Update User...` is preserved verbatim.
5. **F12 on the add screen**: footer advertises `F12=Exit` but the program treats PF12 as an invalid key (`COUSR01C.cbl:98-102`); the target follows the program.
6. **Case**: no upper-casing anywhere in S-12 (unlike COSGN00C); ids are stored as typed.
7. **PF3 on COUSR02C** performs the save and then XCTLs unconditionally (`COUSR02C.cbl:112-119`); any message from the save is never seen. The target mirrors this (save command, then navigate whatever the outcome).
