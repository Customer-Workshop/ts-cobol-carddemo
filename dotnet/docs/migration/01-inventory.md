# Module Inventory

Granularity: one module = one program (plus its copybooks/JCL). Runtime types:
B = batch (JCL), C = CICS online, U = callable utility.

## Batch programs

| Program | Type | Purpose | Key copybooks | Calls | JCL |
| --- | --- | --- | --- | --- | --- |
| CBACT01C | B | Account file reader/print | CVACT01Y | COBDATFT (asm), CEE3ABD | READACCT |
| CBACT02C | B | Card file reader/print | CVACT02Y | CEE3ABD | READCARD |
| CBACT03C | B | Account xref reader/print | CVACT03Y | CEE3ABD | READXREF |
| CBACT04C | B | Interest calculator (updates ACCTFILE, writes TRANSACT) | CVACT01Y CVACT03Y CVTRA01Y CVTRA02Y | CEE3ABD | INTCALC |
| CBCUS01C | B | Customer file reader/print | CVCUS01Y | CEE3ABD | READCUST |
| CBTRN01C | B | Daily transaction verify | CVTRA05Y CVACT01Y CVACT03Y | CEE3ABD | — |
| CBTRN02C | B | Transaction poster (rejects, tcatbal update) | CVTRA05Y CVTRA01Y CVACT01Y | CEE3ABD | POSTTRAN |
| CBTRN03C | B | Transaction detail report (multi-page, date window) | CVTRA05Y CVTRA03Y CVTRA04Y CVTRA07Y | CEE3ABD | TRANREPT |
| CBSTM03A/B | B | Statement creation (GDG, 2 outputs) | CVACT03Y COSTM01 | CBSTM03B | CREASTMT |
| CBEXPORT / CBIMPORT | B | Data export/import | multiple | CEE3ABD | CBEXPORT/CBIMPORT |
| CSUTLDTC | U | Date validation (CEEDAYS wrapper) | — | — | (CALLed) |
| COBSWAIT | B | Wait utility | — | MVSWAIT (asm) | WAITSTEP |

## CICS programs (data+COMMAREA oracle level only)

COSGN00C (sign-on), COMEN01C/COADM01C (menus), COACTVWC/COACTUPC (account
view/update), COCRDLIC/COCRDSLC/COCRDUPC (cards), COTRN00C/01C/02C
(transactions), COBIL00C (bill pay), CORPT00C (report submit → INTRDR),
COUSR00C–03C (user admin). All share COCOM01Y COMMAREA, CSDAT01Y/CSMSG01Y/
CSUSR01Y and the BMS map copybooks.

## Dependency DAG (batch)

- Shared decoders (wave 0) ← everything.
- CBACT04C depends only on data files + CEE3ABD stub → **leaf, first module**.
- CBTRN03C depends only on data files → leaf.
- CBTRN02C feeds TCATBAL consumed by CBACT04C (runtime data dependency, not code).
- CBACT01C blocked on COBDATFT (asm) decision; CBSTM03A blocked on CBSTM03B.
- CICS modules depend on shared decoders + (functionally) the batch-maintained files.

## Dead / out-of-scope code

`COBSWAIT`/`MVSWAIT` (scheduler wait), `TXT2PDF` JCL utilities: no business data
output; excluded from wave planning.

## Copybook → C# mapping (wave 0)

| Copybook | Record | Bytes | C# type |
| --- | --- | --- | --- |
| CVACT01Y | ACCOUNT-RECORD | 300 | `CardDemo.Legacy.Decoders.Records.AccountRecord` |
| CVTRA01Y | TRAN-CAT-BAL-RECORD | 50 | `TranCatBalRecord` |
| CVACT03Y | CARD-XREF-RECORD | 50 | `CardXrefRecord` |
| CVTRA02Y | DIS-GROUP-RECORD | 50 | `DisclosureGroupRecord` |
| CVTRA05Y | TRAN-RECORD | 350 | `TransactionRecord` |

Field mapping rules: `PIC 9(n)` unsigned zoned → `long` via `ZonedDecimal.DecodeUnsigned`;
`PIC S9(p)V9(s)` signed zoned (EBCDIC overpunch) → `decimal` via `ZonedDecimal.DecodeSigned(scale)`;
`PIC X(n)` → `string` (ASCII, space padded). Encode is byte-exact inverse (preferred
positive overpunch `{A–I`, negative `}J–R`), proven by round-trip tests against golden bytes.
