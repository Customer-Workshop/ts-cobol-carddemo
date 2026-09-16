# S-03 Account Update — Stream Functional Requirements (`!mf_stream_fr_generation`)

Source: `app/cbl/COACTUPC.cbl` (cites `:line` below are into this file unless prefixed), `app/bms/COACTUP.bms`,
`app/cpy/CSUTLDPY.cpy`, `app/cpy/CSLKPCDY.cpy`, `app/cpy/CSSETATY.cpy`. Companion docs: `S03_account_update_analysis.md`,
`S03_account_update_migration_plan.md`, `programs/COACTUPC_functional_requirement.md`.

## 1. Purpose and scope
Let an authenticated CardDemo user look up one account by id, review the account master + associated customer master values,
edit them on a single screen, have every edit validated in the legacy order with the legacy messages, confirm with F5, and have both
records written atomically with optimistic-concurrency protection. Scope = transaction `CAUP`, program `COACTUPC`, map `CACTUPA`.
Out of scope: any card-side function (`COCRDUPC`, `COCRDLIC`, `COCRDSLC`) — stays behind the disabled route registry (S01-B1).

## 2. Actors and preconditions
- Actor: any signed-on user (`U` or `A`). COACTUPC has no `CDEMO-USRTYP-ADMIN` gate (no reference in the program) → route is `authGuard` only.
- Precondition: reached from the main menu option 02 (`CDEMO-FROM-PROGRAM = 'COMEN01C'`, :880-897) or re-entered via `CAUP` with its own COMMAREA.

## 3. Surface specification
### Account update screen CACTUPA (`app/bms/COACTUP.bms`)
Field table, lengths and protection per state: analysis doc §3. Message areas: INFOMSG row 22 len 45 (neutral), ERRMSG row 23 len 78 (red bright).
Function-key line row 24: `ENTER=Process F3=Exit` always; `F5=Save` bright only in Confirm state; `F12=Cancel` bright when changes made and not yet done (:3566-3584).

## 4. Functional requirements (KEEP)

| ID | Flow | Business trigger | Observable result | Cite | Boundary | Covering test |
|---|---|---|---|---|---|---|
| FR-S03-01 | Entry | Arrive from menu (first entry / not re-enter) | Search state: blank details, info `Enter or update id of account to update`, account id field editable, cursor on it | :880-897, :964-975, :2955-2962, :2994-2995 | S03-B3 | `AccountUpdateComponent` spec (initial state) |
| FR-S03-02 | Search edit | ENTER with account id blank (or `*`) | Error `No input received`; stay in Search | :1057-1064, :1783-1795, :1434-1440 | — | `AccountUpdateEditRulesTests`, component spec |
| FR-S03-03 | Search edit | ENTER with account id not 11 digits or all zeros | Error `Account Number if supplied must be a 11 digit Non-Zero Number`; stay in Search | :1797-1815 | — | unit + integration |
| FR-S03-04 | Lookup | Valid id; no xref row for the account | Error `Account:<id> not found in Cross ref file.  Resp:000000013  Reas:0000` (75-char cut of the STRING, §11); stay in Search | :3650-3698 | S03-B2 | integration |
| FR-S03-05 | Lookup | Xref found; account master missing | Error `Account:<id> not found in Acct Master file.Resp:000000013  Reas:0000`; stay in Search (deviation D2 §11) | :3701-3748, :3624-3628 | S03-B2 | integration |
| FR-S03-06 | Lookup | Account found; customer master missing | Error `CustId:<custid> not found in customer master.Resp: 000000013  REAS:0000000`; stay in Search | :3752-3797, :3630-3637 | S03-B2 | integration |
| FR-S03-07 | Lookup | Xref + account + customer found | Details state: originals displayed with legacy derivations (money `+ZZZ,ZZZ,ZZZ.99`, dates split y/m/d, SSN 3/2/4, phone 3/3/4, zip first 5), info `Update account details presented above.`, editable fields per map, customer id + country protected | :2568-2580, :3801-3885, :2787-2867, :3500-3562 | S03-B2 | integration + component spec |
| FR-S03-08 | Change detection | ENTER in Details/Edit-error with no field different from fetched (text compared trimmed + case-insensitive; money compared numerically; dates/SSN/phone/EFT/FICO compared exactly) | Error `No change detected with respect to values fetched.`; stay in Details with originals; cursor on status | :1681-1777, :1462-1467, :3016-3018 | — | unit `AccountUpdateEditRulesTests`, integration |
| FR-S03-09 | Validation order | Any change present | Edits run in this order, first failing edit supplies the message (later edits still flag their field): Account Status, Open Date, Credit Limit, Expiry Date, Cash Credit Limit, Reissue Date, Current Balance, Current Cycle Credit Limit, Current Cycle Debit Limit, SSN, Date of Birth, FICO Score, First Name, Middle Name, Last Name, Address Line 1, State, Zip, City, Country, Phone Number 1, Phone Number 2, EFT Account Id, Primary Card Holder, State/Zip combination | :1469-1677 | — | unit |
| FR-S03-10 | Y/N edits | Account Status / Primary Card Holder blank (`*`, spaces, zeros) or not `Y`/`N` | `<name> must be supplied.` / `<name> must be Y or N.` | :1856-1894 | — | unit |
| FR-S03-11 | Date edits | Open / Expiry / Reissue / Date of Birth parts | Year: `<name> : Year must be supplied.`, `<name> must be 4 digit number.`, `<name> : Century is not valid.` (only 19xx/20xx); Month: `<name> : Month must be supplied.`, `<name>: Month must be a number between 1 and 12.`; Day: `<name> : Day must be supplied.`, `<name>:day must be a number between 1 and 31.`; combination: `<name>:Cannot have 31 days in this month.`, `<name>:Cannot have 30 days in this month.`, `<name>:Not a leap year.Cannot have 29 days in this month.` | CSUTLDPY.cpy:19-283 | — | unit |
| FR-S03-12 | DOB future | Date of Birth structurally valid but not strictly before today | `Date of Birth:cannot be in the future ` | CSUTLDPY.cpy:333-360, :1533-1543 | — | unit |
| FR-S03-13 | Money edits | Credit Limit, Cash Credit Limit, Current Balance, Current Cycle Credit Limit, Current Cycle Debit Limit | Blank → `<name> must be supplied.`; not NUMVAL-C parsable (sign, thousands separators, decimal point, optional `$`, trailing `CR`/`DB`) → `<name> is not valid` | :2180-2221, :1074-1160 | — | unit |
| FR-S03-14 | SSN | Parts 3/2/4 | Each part via numeric-required edit with names `SSN: First 3 chars`, `SSN 4th & 5th chars`, `SSN Last 4 chars` (`… must be supplied.`, `… must be all numeric.`, `… must not be zero.`); part 1 additionally `SSN: First 3 chars: should not be 000, 666, or between 900 and 999` for 666 or 900-999 | :2431-2489, :119-123 | — | unit |
| FR-S03-15 | FICO | 3 chars | Numeric-required edit (`FICO Score must be supplied.` / `… must be all numeric.` / `… must not be zero.`), then range → `FICO Score: should be between 300 and 850` | :1545-1558, :2514-2531, :848-849 | — | unit |
| FR-S03-16 | Names / city / state / country | Required alphabetic | Blank → `<name> must be supplied.`; any char other than A-Z/a-z/space → `<name> can have alphabets only.`; Middle Name optional (blank OK, else alphabetic) | :1898-1951, :2012-2057, :1560-1582, :1615-1630 | — | unit |
| FR-S03-17 | Address line 1 | Mandatory | Blank → `Address Line 1 must be supplied.` (any content accepted); Address Line 2 not edited | :1824-1852, :1584-1590, :1614 | — | unit |
| FR-S03-18 | State | 2 chars | Alphabetic-required, then `State: is not a valid state code` unless in the 56-code US list | :2493-2511, CSLKPCDY.cpy:1012-1069 | — | unit |
| FR-S03-19 | Zip | 5 chars | Numeric-required (`Zip must be supplied.`, `Zip must be all numeric.`, `Zip must not be zero.`) | :1605-1612, :2109-2176 | — | unit |
| FR-S03-20 | State + Zip | Both individually valid | `Invalid zip code for state` unless `<state><zip(1:2)>` is in the 240-combo list; both fields flagged | :1663-1668, :2536-2558, CSLKPCDY.cpy:1071-1313 | — | unit |
| FR-S03-21 | Phones | 3/3/4 parts, two phones | All parts blank → accepted. Otherwise, per part in order area/prefix/line: `<name>: Area code must be supplied.`, `<name>: Area code must be A 3 digit number.`, `<name>: Area code cannot be zero`, `<name>: Not valid North America general purpose area code` (410-code list); `<name>: Prefix code must be supplied.` / `… must be A 3 digit number.` / `… cannot be zero`; `<name>: Line number code must be supplied.` / `… must be A 4 digit number.` / `… cannot be zero` | :2225-2427, CSLKPCDY.cpy:521-930 | — | unit |
| FR-S03-22 | EFT account id | 10 chars | Numeric-required (`EFT Account Id must be supplied.` / `… must be all numeric.` / `… must not be zero.`) | :1648-1655 | — | unit |
| FR-S03-23 | Validation passed | All edits OK | Confirm state: info `Changes validated.Press F5 to save`, user's values shown, all fields protected, F5/F12 lit; ENTER in this state redisplays unchanged | :1670-1674, :2582-2590, :2617-2620, :2966-2967, :3000-3002, :3577-3580 | — | integration + component spec |
| FR-S03-24 | Validation failed | Any edit failed | Edit-error state: error line = first message, invalid fields highlighted (blank-invalid shown as `*`), values as typed, F12 lit | :1469, :2596-2598, CSSETATY.cpy | — | component spec |
| FR-S03-25 | Save | F5 in Confirm state | Lock account (READ UPDATE) then customer; compare current stored account+customer to the fetched snapshot; REWRITE account then customer; success → Done state: info `Changes committed to database`, all protected | :2600-2612, :3888-4105, :2968-2969 | S03-B2 | integration |
| FR-S03-26 | Save — lock | Account record cannot be read for update | Error `Could not lock account record for update`, info `Changes unsuccessful. Please try again`, values kept, account id editable | :3903-3912, :2604-2605, :2971-2974 | S03-B2 | integration (NOWAIT lock contention) |
| FR-S03-27 | Save — lock | Customer record cannot be read for update | Error `Could not lock customer record for update`, info `Changes unsuccessful. Please try again` (deviation D1 §11) | :3924-3933 | S03-B2 | integration |
| FR-S03-28 | Save — concurrency | Stored account or customer differs from fetched snapshot (account: status, 5 amounts, 3 dates, group id case-insensitive; customer: names/address/state/country/govt-id case-insensitive, zip/phones/SSN/DOB/EFT/primary-ind/FICO exact) | Error `Record changed by some one else. Please review`; back to Details showing fetched originals; nothing written | :4109-4200, :2608-2609 | S03-B2 | integration |
| FR-S03-29 | Save — write failure | REWRITE of account or customer fails | Error `Update of record failed`, info `Changes unsuccessful. Please try again`; whole unit rolled back | :4079-4105, :2606-2607 | S03-B2 | integration (constraint violation) |
| FR-S03-30 | After commit | ENTER in Done state | Back to Details for the same account (target re-fetches; deviation D3 §11) | :2626-2634 | — | component spec |
| FR-S03-31 | Cancel | F12 once details fetched (any of Details/Edit-error/Confirm/Done/Failed) | Re-read account+customer, discard edits, Details state, message cleared | :908-915, :2571-2580 | S03-B2 | integration + component spec |
| FR-S03-32 | Exit | F3 in any state | Return to the calling menu (`/menu`); no write | :927-960 | S03-B1 | component spec |
| FR-S03-33 | Invalid AID | Any other function key (F5 outside Confirm, F12 before fetch, F1/F2/F4/…) | Treated as ENTER by the source (`SET CCARD-AID-ENTER`); web target shows `Invalid key pressed. Please see below...` per S-01 AID parity (FR-S01-20) and leaves the screen as is | :905-915 | — | component spec |
| FR-S03-34 | Data mapping on write | Save succeeds | Account: status, 5 amounts (S9(10)V99), open/expiry/reissue dates `YYYY-MM-DD`, group id; Customer: names, address 1/2/3(city), state, country (unchanged), zip (5 typed chars), phones re-stringed `(aaa)bbb-cccc`, SSN 9 digits, govt id, DOB, EFT id, primary-holder ind, FICO | :3945-4050 | S03-B2 | integration |

## 5. Validation and error catalogue
| Message | Trigger | Cite | Blocking? | Resulting state |
|---|---|---|---|---|
| `No input received` | blank account id | :1438-1440 | yes | Search |
| `Account Number if supplied must be a 11 digit Non-Zero Number` | non-numeric / zero id | :1801-1808 | yes | Search |
| `Account:<11> not found in Cross ref file.  Resp:<10> Reas:<10>` | xref miss | :3669-3684 | yes | Search |
| `Account:<11> not found in Acct Master file.Resp:<10> Reas:<10>` | account miss | :3717-3733 | yes | Search |
| `CustId:<9> not found in customer master.Resp: <10> REAS:<10>` | customer miss | :3766-3783 | yes | Search |
| `File Error: READ     on <file>  returned RESP <10>,RESP2 <10>` | other file error | :389-408 | yes | Search |
| `No change detected with respect to values fetched.` | nothing changed | :491-492 | yes | Details |
| field messages FR-S03-10..22 | edit failure | §4 | yes | Edit error |
| `Changes validated.Press F5 to save` (info) | edits OK | :472-473 | — | Confirm |
| `Changes committed to database` (info) | save OK | :474-475 | — | Done |
| `Could not lock account record for update` / `Could not lock customer record for update` | lock failure | :517-520 | yes | Failed |
| `Record changed by some one else. Please review` | snapshot mismatch | :521-522 | yes | Details (originals) |
| `Update of record failed` | write failure | :523-524 | yes | Failed |
| `Changes unsuccessful. Please try again` (info) | Failed state | :476-477 | — | Failed |
| `Invalid key pressed. Please see below...` | unmapped AID (web) | CSMSG01Y.cpy:20-21 (S-01) | no | unchanged |

Unused 88 literals (`Account number not provided` is overwritten by `No input received`; `Account number must be a non zero 11 digit number`,
`Did not find this account in account card xref file`, `… account master file`, `… associated customer in master file`, `Last name not provided`,
`Name can only contain alphabets and spaces`, `Credit Limit …`, `Card expiry …`, `Looks Good.... so far`) are never displayed and are **not** ported.

## 6. Field and data derivations
See analysis §4. Money input accepted per `TEST-NUMVAL-C`; stored as `decimal(12,2)`. Dates stored as `DateOnly?`; null shows as blank parts.
Phones stored raw `(aaa)bbb-cccc`; blank phone is written as `()-` exactly as the STRING statements do (:4006-4020).
Zip written = the 5 typed characters (record field is 10 wide, trailing blanks trimmed by the shared import convention).

## 7. Mechanics (demoted, cited)
CICS HANDLE ABEND / ABEND-ROUTINE (:4203-4222), SEND MAP ERASE/CURSOR (:3589-3603), COMMAREA marshalling (:1007-1019), `CSUTLDTC` LE date
call (CSUTLDPY.cpy:284-323), DFHBM* attribute bytes, `SYNCPOINT` before XCTL (:951-954).

## 8. Acceptance criteria (Given/When/Then) — one per FR
- FR-S03-01 Given menu entry, When screen opens, Then details blank, info prompt shown, id editable.
- FR-S03-02/03 Given Search, When ENTER with blank / `abc` / `00000000000`, Then the cited message, still Search.
- FR-S03-04..06 Given seeded Postgres, When id has no xref / no account / no customer, Then the cited 75-character message.
- FR-S03-07 Given seeded account 00000000001, When ENTER, Then originals shown with derivations, info `Update account details presented above.`.
- FR-S03-08 Given Details, When ENTER unchanged (or only case/trailing-space differences in text), Then `No change detected with respect to values fetched.`.
- FR-S03-09..22 Given Details with several invalid fields, When ENTER, Then the message of the earliest field in the order list; all invalid fields flagged.
- FR-S03-23 Given all edits valid, When ENTER, Then Confirm state; ENTER again → unchanged.
- FR-S03-25 Given Confirm, When F5, Then rows updated, `Changes committed to database`.
- FR-S03-26/27 Given another transaction holds `FOR UPDATE` on the row, When F5, Then lock message + failure info, nothing written.
- FR-S03-28 Given the row changed after fetch, When F5, Then `Record changed by some one else. Please review`, nothing written, originals shown.
- FR-S03-29 Given a write that violates a constraint, When F5, Then `Update of record failed`, both rows unchanged.
- FR-S03-30 Given Done, When ENTER, Then Details for the same id with current stored values.
- FR-S03-31 Given edits typed, When F12, Then originals restored, Details, message cleared.
- FR-S03-32 Given any state, When F3, Then `/menu`.
- FR-S03-33 Given any state, When F7, Then `Invalid key pressed. Please see below...`.
- FR-S03-34 Given save OK, When rows re-read, Then values mapped as listed.

## 9. Traceability matrix
| FR | Backend | Frontend |
|---|---|---|
| 01, 24, 30-33 | — | `account-update.component.ts` + spec |
| 02-03 | `AccountUpdateEditRules.EditAccountId` | component (message display) |
| 04-07 | `AccountUpdateService.LookupAsync` (`POST /api/v1/account-update/lookup`) | component |
| 08-22 | `AccountUpdateEditRules` (`POST /api/v1/account-update/validate`) | component (flagging) |
| 23, 25-29, 34 | `AccountUpdateService.SaveAsync` (`POST /api/v1/account-update/save`), `AccountUpdateRepository` | component |

## 10. Program index
| Program | Transaction | Map | FR doc |
|---|---|---|---|
| COACTUPC | CAUP | CACTUPA/COACTUP | `programs/COACTUPC_functional_requirement.md` |

## 11. Open questions and assumptions (recorded deviations)
- **D1 (FR-S03-27)** Source: customer lock failure sets the error message but `2000-DECIDE-ACTION` has no WHEN for it, so it falls to `WHEN OTHER` →
  `ACUP-CHANGES-OKAYED-AND-DONE` and the info line says `Changes committed to database` although nothing was written (:2600-2612). Target: Failed state
  with `Changes unsuccessful. Please try again`. Reason: the source outcome contradicts its own error message and data.
- **D2 (FR-S03-05)** Source tests `DID-NOT-FIND-ACCT-IN-ACCTDAT` (an 88 whose SET is commented out, :3720) so an account-master miss continues into the
  customer read and, if the xref customer exists, shows details built from an unread account buffer (:3624-3646). Target stops at the account-master miss
  with the same message. Reason: the continued path displays undefined data.
- **D3 (FR-S03-30)** Source redisplays the pre-update `ACUP-OLD` snapshot after Done+ENTER (:2626-2634 → 3202). Target re-fetches the account so the
  Details state shows what is stored. Reason: the stale snapshot would immediately trigger a spurious concurrent-change error on the next save.
- **D4** Source abends (`UNEXPECTED DATA SCENARIO`, :2635-2642) when ENTER is pressed in Failed state with no field changed. Target treats Failed
  like Details on ENTER (compare/validate). Reason: an abend is not a portable behaviour.
- **D5** Blank handling: the source converts blank screen fields to LOW-VALUES and `TRIM` does not strip LOW-VALUES, so an untouched blank optional
  field (middle name, address line 2, phone parts) can register as a change (:1681-1777). Target treats blank/`*`/spaces uniformly as empty so untouched
  blank fields are not changes. Reason: phantom changes.
- **D6** Lock parity uses `SELECT … FOR UPDATE NOWAIT` inside one transaction; only a concurrent writer holding the row produces FR-S03-26/27.
- Assumption: RESP/RESP2 for a not-found read are the CICS NOTFND codes `13`/`80`. `WS-RESP-CD`/`WS-REAS-CD` are `PIC S9(09) COMP` (:40-43) so the
  `MOVE` to the `PIC X(10)` `ERROR-RESP`/`ERROR-RESP2` renders `000000013 ` / `000000080 `. Every message passes through `WS-RETURN-MSG PIC X(75)`
  (:479), so the STRING output is cut at 75 characters; the target renders the identical 75-character text, e.g.
  `Account:00000000099 not found in Cross ref file.  Resp:000000013  Reas:0000`.
