# S-21 Transaction-Type Maintenance — Stream Functional Requirements (`!mf_stream_fr_generation`)

Stream: S-21 (ONLINE screens + BATCH jobs). Analysis: `S21_tran_type_maintenance_analysis.md`. Plan: `S21_tran_type_maintenance_migration_plan.md`.
Program FRs: `programs/COTRTLIC_*.md`, `programs/COTRTUPC_*.md`, `programs/COBTUPDT_*.md`.

## 1. Purpose and scope

Admin-side maintenance of the transaction-type reference tables: a paged, filterable list (CTLI) that also flags rows for update/delete (single-action, F10-confirmed), and a single-record add/update/delete screen (CTTU) driven by a confirm/cancel state machine; plus the batch apply (MNTTRDB2/COBTUPDT) and extract (TRANEXTR) legs.

## 2. Actors and preconditions

- Admin user type 'A' via COADM01C options 5/6.
- Preconditions: `transaction_types`/`transaction_categories` exist (V2) + FK RESTRICT (V2801); seed data present.

## 3. Surface specification

### List screen (`bms/COTRTLI.bms` CTRLIA) → `/ui/tran-types`

INPUT: `trType` filter X(2) numeric-or-blank; `trDesc` filter ≤50 (LIKE %x%); `trtsel[1..7]` X(1) D/U flag.
DISPLAY: 7 rows (type, description), page no, messages.
Buttons/keys: Enter=apply filters/selections, F2=Add, F3=Exit, F7=Page Up, F8=Page Dn, F10=Save/Confirm flagged action.

### Maint screen (`bms/COTRTUP.bms` CTRTUPA) → `/ui/tran-types/maint`

INPUT: `trtypcd` X(2), `trtydsc` X(50). DISPLAY: info + error lines.
Keys: Enter=process, F4=Delete, F5=Save, F6=Add, F12=Cancel, F3=Exit.

### API endpoints

`GET /api/tran-types?type=&desc=&after=&dir=` (paged+filtered), `GET /api/tran-types/{code}`, `POST /api/tran-types` (insert), `PUT /api/tran-types/{code}` (update), `DELETE /api/tran-types/{code}`; plus flag-action endpoint for list-page updates `POST /api/tran-types/{code}/flag-action`? — simplified: the list page posts `{action:'U'|'D', rows:[{code,desc}]}` once.
Batch: `BatchJobService` jobs `tranTypeMaintJob` (params: `inputFile`) and `tranTypeExtractJob`.

## 4. Functional requirements (KEEP)

| ID | Flow | Business trigger | Observable result | Program | Cite | Boundary | Covering test |
|---|---|---|---|---|---|---|---|
| FR-S21-01 | admin route | menu opt 5/6 | list/maint screens reachable | COADM01C→ | COADM02Y.cpy:46-49 | S21-B1 | menu flag test |
| FR-S21-02 | list render | entry | ≤7 rows in TR_TYPE order | COTRTLIC | :339-368 | S21-B3 | list test |
| FR-S21-03 | filter | Enter w/ type/desc | filtered page or 'No Records found for these filter conditions' | COTRTLIC | :1111-1163,:1248-1266 | S21-B3 | filter test |
| FR-S21-04 | page | F7/F8 | prev/next page; edge messages | COTRTLIC | :1534-1548 | S21-B3 | paging test |
| FR-S21-05 | flag D/U | one row flagged, F10 | update write or delete; confirm messages | COTRTLIC | :1021-1052,:1846-1935 | S21-B3 | flag-flow test |
| FR-S21-06 | flag invalid | >1 action or bad code | 'Please select only 1 action' / 'Action code selected is invalid' | COTRTLIC | :1021-1052 | — | validation test |
| FR-S21-07 | add | F2 | maint screen in create state | COTRTLIC→COTRTUPC | :630-651 | S21-B2 | nav test |
| FR-S21-08 | maint validate | Enter w/ fields | required/numeric/non-zero code; desc required ≤50; 'No input received'/'No change detected...' paths | COTRTUPC | :758-972 | — | validation test |
| FR-S21-09 | maint save | F5 (state N) | INSERT (miss) or UPDATE; 'Changes committed to database'; -911 lock msg | COTRTUPC | :1544-1589 | S21-B3 | write test |
| FR-S21-10 | maint delete | F4→confirm (state 9→8) | DELETE; -532 child-guard message | COTRTUPC | :1638-1649 | S21-B4 | delete test |
| FR-S21-11 | maint cancel | F12 | originals restored; 'Update was cancelled'/'Delete was cancelled' | COTRTUPC | :988-1008 | — | cancel test |
| FR-S21-12 | exit | F3 | back to caller (COADM01C or CTLI) | both | :591-623,:429-460 | B-012 | nav test |
| FR-S21-13 | batch apply | job run w/ INPFILE | A→insert, U→update, D→delete, '*'→skip; bad type→fail RC4 | COBTUPDT | :109-129 | S21-B5 | job IT |
| FR-S21-14 | extract | extract job | 60-char TRANTYPE.PS & TRANCATG.PS equivalents | DSNTIAUL via TRANEXTR | jcl/TRANEXTR.jcl | S21-B6 | export layout test |

## 5. Validation and error catalogue

| Condition | Message (verbatim) | Cite |
|---|---|---|
| filter not 2-digit | `TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER` | COTRTLIC.cbl:1111-1118 |
| multi action | `Please select only 1 action` | :1021-1052 |
| bad action code | `Action code selected is invalid` | same |
| no rows | `No Records found for these filter conditions` | :1248-1266 |
| page edges | `No previous pages to display` / `No more pages to display` / `No more pages for these search conditions` | :1534-1548 |
| update miss | `Record not found. Deleted by others ?` | :1846-1893 |
| deadlock | `Deadlock. Someone else updating ?` | same |
| child rows exist | `Please delete associated child records first:` | :1900-1935, COTRTUPC.cbl:1638-1649 |
| maint field errs | `Transaction Type: Enter a valid 2 digit code` (required/numeric/non-zero family) / `Description is required` family | COTRTUPC.cbl:758-972 |
| maint save results | `Changes committed to database` / `Changes unsuccessful` / `Could not lock record for update` / `Error updating: TRANSACTION_TYPE Table. SQLCODE:` | :1544-1649 |
| maint misc | `No record found for this key in database` / `No input received` / `Invalid key pressed` / `No change detected with respect to values fetched.` | :160-196,:783-811 |
| batch bad type | `ERROR: TYPE NOT VALID` + RC4 | COBTUPDT.cbl:129,:230-233 |
| batch update miss | `No records found.` + abend | COBTUPDT.cbl |

## 6. Field and data derivations

- List row DESC edit = `TR_DESCRIPTION` trimmed; desc filter wraps `'%'+value+'%'` for LIKE (:1155-1163).
- Type code canonical form = 2-digit zero-padded numeric (:907-972).
- F10 only saves when a delete/update flag is pending AND no filter/row changes, else treated as ENTER (:666-678).
- Extract layouts: `type||desc(50)||'0'*8` and `type||cat||data(50)||'0'*4` — 60 bytes each.

## 7. Mechanics (demoted, cited)

Cursors → keyset pagination; SYNCPOINT → @Transactional; `9998-PRIMING-QUERY` → entry connectivity check; `9999-FORMAT-DB2-MESSAGE`/`DSNTIAC` → `Db2ErrorFormatter`; HANDLE ABEND + ABCODE (:1675-1697) → global 500 handler; RETURN TRANSID → session.

## 8. Acceptance criteria (Given/When/Then)

- FR-S21-02..05: Given ≥8 types, When admin lists/filters/flags D on row 1 + F10, Then paged filtered list shows the row deleted w/ confirm messages.
- FR-S21-06: Given 2 rows flagged, When F10, Then 'Please select only 1 action'.
- FR-S21-08..11: Given maint screen, When Enter no input / save valid / F4 confirm / F12 cancel, Then the matching messages and DB effects occur.
- FR-S21-13: Given INPFILE with A/U/D/*/bad records, Then valid actions apply and bad record fails the job RC4.
- FR-S21-14: Given rows, Then extract emits exact 60-char layouts.

## 9. Traceability matrix

| FR | Program FR | Java surface |
|---|---|---|
| FR-S21-01..07 | COTRTLIC-nn | `TranTypeService`/list page |
| FR-S21-08..12 | COTRTUPC-nn | maint controller/page |
| FR-S21-13 | COBTUPDT-nn | `tranTypeMaintJob` |
| FR-S21-14 | TRANEXTR row | `tranTypeExtractJob` |

## 10. Program index

| Program | Role | Program FR |
|---|---|---|
| COTRTLIC | list + flagged updates | programs/COTRTLIC_functional_requirement.md |
| COTRTUPC | maint state machine | programs/COTRTUPC_functional_requirement.md |
| COBTUPDT | batch apply | programs/COBTUPDT_functional_requirement.md |

## 11. Open questions and assumptions

1. `transaction_categories.tran_category_code` is `integer` in baseline vs DB2 `CHAR(4)` — preserve zero-padded 4-char rendering; numeric input pads.
2. TRANEXTR's GDG backups (STEP10-30) map to versioned export files — kept optional; extract content is the contract.
3. CREADB21 load job is a one-time bootstrap — covered by seed data, not ported as a job.
