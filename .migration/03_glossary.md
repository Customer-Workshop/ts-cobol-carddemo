# 03_glossary — CardDemo vocabulary

- **Module**: the CardDemo application estate under `app/` (core) plus optional extensions.
- **Stream**: one selectable end-to-end unit (a menu option's screen flow, or one scheduler-
  triggered batch job chain) migratable on its own.
- **Transaction / trancode**: CICS 4-char code (CC00 sign-on, CM00 main menu, CA00 admin menu...).
- **Sub-transaction**: called sub-flow / shared subroutine (e.g. CSUTLDTC) with its own contract.
- **COMMAREA**: CICS memory block passed between programs; carries pseudo-conversational state.
- **BMS map**: 3270 screen definition (`app/bms`); symbolic copybooks in `app/cpy-bms`.
- **KSDS / AIX**: VSAM key-sequenced dataset / alternate index.
- **GDG**: generation data group (versioned batch files).
- **POSTTRAN**: daily transaction-posting batch chain (CBTRN* programs).
- **INTCALC**: monthly interest calculation batch.
- **CBEXPORT / CBIMPORT**: cross-branch data export/import batch utilities.
- **SMART folder / INCOND / OUTCOND**: Control-M job containers and dependency conditions.
- **COMP / COMP-3**: binary / packed-decimal storage; overpunch: sign embedded in last digit of
  zoned decimal.
- **REDEFINES / OCCURS**: COBOL overlay / array constructs in copybooks.
- **UniKix region**: CICS-compatible Linux runtime (rehost artifacts in `samples/m2/unikix`).
- **FCT / PCT**: file / program control tables mapping resources into the runtime.
