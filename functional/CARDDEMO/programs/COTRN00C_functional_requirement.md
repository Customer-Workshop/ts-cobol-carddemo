# COTRN00C — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: COTRN00C — `app/cbl/COTRN00C.cbl`. Stream S-07, **wave 1** (single-program stream).
- Role: entry/list — browses the TRANSACT KSDS ten rows at a time in key order, supports search-by-key, forward/backward paging and row selection for detail view.

## 2. Trigger / caller contract
- CICS transaction `CT00` (`app/csd/CARDDEMO.CSD:419-420`), reached by XCTL from the main menu (COMEN01C option 06, `app/cpy/COMEN02Y.cpy:58`) with `CARDDEMO-COMMAREA`; `EIBCALEN = 0` bounces to COSGN00C (`COTRN00C.cbl:107-109`). Re-entered pseudo-conversationally with its own COMMAREA (`:138-141`).
- COMMAREA extension `CDEMO-CT00-INFO` (`:62-70`): TRNID-FIRST X(16), TRNID-LAST X(16), PAGE-NUM 9(08), NEXT-PAGE-FLG X(1), TRN-SEL-FLG X(1), TRN-SELECTED X(16).
- Outbound: XCTL COTRN01C with FROM-TRANID='CT00', FROM-PROGRAM='COTRN00C', PGM-CONTEXT=0 and the selected id (`:186-195`); XCTL COMEN01C on PF3 (`:122-124`, `:510-521`).

## 3. Inputs and outputs
Inputs (map COTRN0A / mapset COTRN00, `app/cpy-bms/COTRN00.CPY`): TRNIDINI X(16) (dict: transactions.tran_id), SEL0001I..SEL0010I X(1), and — because all row fields are FSET — the previously displayed TRNID/TDATE/TDESC/TAMT rows and PAGENUM. AID key (EIBAID).
Reads: TRANSACT record TRAN-RECORD (`app/cpy/CVTRA05Y.cpy`), key TRAN-ID; fields TRAN-ID, TRAN-DESC, TRAN-AMT, TRAN-ORIG-TS.
Outputs: TRNID01..10 X(16), TDATE01..10 X(8) `mm/dd/yy`, TDESC01..10 X(26), TAMT001..010 X(12) `+99999999.99`, PAGENUMI X(8), ERRMSGO X(78), TRNIDINO (cleared after a forward page `:325`), header fields (`:567-586`), outgoing COMMAREA.

## 4. Functional requirements owned (all cross-ref stream FR)
| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| COTRN00C-01 | EIBCALEN = 0 | transfer to sign-on | :107-109 | FR-S07-01 |
| COTRN00C-02 | first entry (PGM-CONTEXT enter) | page 1 from start of file, blank search | :112-116 | FR-S07-02 |
| COTRN00C-03 | ENTER, blank search id | browse from LOW-VALUES, page 1 | :206-207, :224-225 | FR-S07-03 |
| COTRN00C-04 | ENTER, 16-digit search id | browse GTEQ from key, page 1, search cleared | :209-210, :228, :325 | FR-S07-04 |
| COTRN00C-05 | ENTER, non-numeric search id | `Tran ID must be Numeric ...`, nothing else changes | :211-218, :283 | FR-S07-05 |
| COTRN00C-06 | page populated | rows edited (date/desc/amount), unfilled rows blank | :289-303, :383-445 | FR-S07-06 |
| COTRN00C-07 | 10 rows + successor | page +1, NEXT-PAGE-YES, no message | :305-310 | FR-S07-07 |
| COTRN00C-08 | ENDFILE while filling / on peek | `You have reached the bottom of the page...`, NEXT-PAGE-NO, page +1 if ≥1 row | :297-320, :639-645 | FR-S07-08 |
| COTRN00C-09 | STARTBR NOTFND | `You are at the top of the page...`, rows unchanged, page 0 on ENTER | :605-611 | FR-S07-09 |
| COTRN00C-10 | PF8, NEXT-PAGE-YES | forward from TRNID-LAST, that record skipped | :257-268, :285-287 | FR-S07-10 |
| COTRN00C-11 | PF8, NEXT-PAGE-NO | `You are already at the bottom of the page...`, no erase | :269-273 | FR-S07-11 |
| COTRN00C-12 | PF7, page > 1 | backward from TRNID-FIRST, rows filled 10→1, page −1 or 1 | :234-246, :333-369 | FR-S07-12 |
| COTRN00C-13 | PF7, page ≤ 1 | `You are already at the top of the page...`, no erase, NEXT-PAGE-YES set | :242, :245-251 | FR-S07-13 |
| COTRN00C-14 | READPREV ENDFILE | `You have reached the top of the page...`, page 1 when 10 rows read | :351-369, :673-679 | FR-S07-14 |
| COTRN00C-15 | `S`/`s` on a row with id | XCTL COTRN01C with selected id | :148-195 | FR-S07-15 |
| COTRN00C-16 | other char on a row with id | `Invalid selection. Valid value is S`, processing continues | :196-203 | FR-S07-16 |
| COTRN00C-17 | selection on row without id | ignored | :183-184 | FR-S07-17 |
| COTRN00C-18 | PF3 | XCTL COMEN01C | :122-124 | FR-S07-18 |
| COTRN00C-19 | unmapped AID | `Invalid key pressed. Please see below...` | :129-133 | FR-S07-19 |
| COTRN00C-20 | RESP other on STARTBR/READNEXT/READPREV | `Unable to lookup transaction...`, ERR-FLG on | :612-618, :646-652, :680-686 | FR-S07-20 |
| COTRN00C-21 | any browse | rows in TRAN-ID key order | :593-696 | FR-S07-21 |

## 5. Business rules and validations
ENTER sequence (`:146-229`): selection scan (first non-blank SEL wins) → S/s hand-off (terminal) or invalid-selection message (non-blocking) → search id blank/NUMERIC test (blocking on failure) → page number reset to 0 → forward page from key. PF7 (`:234-252`): NEXT-PAGE-YES forced; page > 1 gate. PF8 (`:257-274`): NEXT-PAGE flag gate. Paging arithmetic and cursor updates per §4 rows 07-14.

## 6. Data access and boundaries
- TRANSACT read-only browse (S07-B3, DECIDED: shared `ITransactionRepository.BrowseAsync` = STARTBR GTEQ + READNEXT×n + peek; `BrowseBackwardAsync` = READPREV×n; exceptions → COTRN00C-20). No writes, no commit scope.
- Hand-off to COTRN01C (S07-B1, DECIDED: route registry `ProgramKey=COTRN01C`, disabled → coming-soon idiom). Return to menu (S07-B2: `/menu`). Entry guard (S07-B4: `authGuard`). Paging state (S07-B5: `TransactionListState`).
- No deviations from source behaviour. Storage: Postgres `transactions` (shared layer), byte-order collation on the key.

## 7. Error and edge behavior
Empty/low-value inputs treated as blank (`:149`, `:206`); a shorter-than-16 search entry is space padded by BMS and fails NUMERIC; NOTFND wording is the "top of the page" text even for keys beyond the end; PF7-at-top forces NEXT-PAGE-YES; selection characters persist across redisplays (FSET); amount high-order digit truncated for |amt| ≥ 10^8 (`PIC +99999999.99`); RECEIVE RESP captured but unchecked (`:556-562`, technical); intermediate SENDs in RESP handlers are followed by the paragraph's final SEND with the same message (one observable screen).

## 8. Hard-stop boundary
Delegates transaction detail (COTRN01C, S-08) and all menu/sign-on behaviour (S-01). Not responsible for transaction add (COTRN02C).

## 9. Demoted mechanics
RETURN TRANSID (`:138-141`); re-enter flag (`:112-113`); SEND ERASE/no-ERASE (`:533-549`); header population (`:567-586`); ENDBR (`:692-696`, incl. INVREQ exposure after failed STARTBR — not reproduced); `DISPLAY 'RESP:'` diagnostics (`:613`, `:647`, `:681`) → structured logging.

## 10. Traceability
COTRN00C-01..21 ↔ FR-S07-01..21 (table §4) ↔ `TransactionListServiceTests` / `TransactionListIntegrationTests` / `transaction-list.component.spec.ts`.
