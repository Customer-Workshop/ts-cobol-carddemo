# COBOL Copybook → PySpark Type-Mapping Notes

This document describes the decisions made when converting COBOL copybook field
definitions into PySpark DataFrame schemas for the CardDemo fixed-width data
files.

## Copybooks Covered

| Copybook | Record Name | Record Length | Data File |
|---|---|---:|---|
| `CVACT01Y.cpy` | ACCOUNT-RECORD | 300 | `app/data/ASCII/acctdata.txt` |
| `CUSTREC.cpy` | CUSTOMER-RECORD | 500 | `app/data/ASCII/custdata.txt` |
| `CVACT02Y.cpy` | CARD-RECORD | 150 | `app/data/ASCII/carddata.txt` |

---

## Type-Mapping Rules

### PIC X(n) → StringType

Alphanumeric fields (`PIC X(n)`) are mapped to `StringType`. Trailing spaces are
stripped during parsing since COBOL pads alphanumeric fields with spaces on the
right.

**Examples:**
- `ACCT-ACTIVE-STATUS  PIC X(01)` → `StringType`
- `CARD-EMBOSSED-NAME  PIC X(50)` → `StringType`
- `CUST-ADDR-LINE-1    PIC X(50)` → `StringType`

### PIC 9(n) → IntegerType or LongType

Unsigned DISPLAY numeric fields are mapped to:
- **`IntegerType`** when `n ≤ 9` (values fit within 32-bit signed integer range)
- **`LongType`** when `n > 9` (values may exceed `2^31 - 1`)

The threshold of 9 digits was chosen because `PIC 9(09)` has a maximum value of
`999,999,999`, which fits in a 32-bit signed integer (`2,147,483,647`). Fields
like `PIC 9(11)` can reach `99,999,999,999`, requiring 64-bit `LongType`.

**Examples:**
- `ACCT-ID       PIC 9(11)` → `LongType`  (11 digits)
- `CUST-ID       PIC 9(09)` → `IntegerType` (9 digits)
- `CARD-CVV-CD   PIC 9(03)` → `IntegerType` (3 digits)

### PIC S9(m)V9(n) → DecimalType(m+n, n)

Signed numeric with implied decimal (`V`) fields are mapped to
`DecimalType(precision, scale)` where:
- **precision** = total integer digits (`m`) + decimal digits (`n`)
- **scale** = decimal digits (`n`)

These fields use COBOL DISPLAY format with a **trailing sign overpunch** on the
last byte. The sign is encoded in the zone nibble of the final character:

| Trailing Char | Digit | Sign |
|:---:|:---:|:---:|
| `{` | 0 | + |
| `A`–`I` | 1–9 | + |
| `}` | 0 | − |
| `J`–`R` | 1–9 | − |

The `V` in the PIC clause denotes an **implied** decimal point — no literal `.`
exists in the data. The parser inserts the decimal programmatically by dividing
the integer result by `10^scale`.

**Example:**
- `ACCT-CURR-BAL  PIC S9(10)V99` → `DecimalType(12, 2)`
  - Raw bytes: `00000001940{` (12 characters)
  - Last char `{` → digit `0`, sign `+`
  - Integer: `000000019400`
  - After implied decimal: `194.00`

### COMP-3 (Packed Decimal) → DecimalType

**Not present in the three copybooks analyzed.** If encountered in other
CardDemo copybooks, COMP-3 fields would be mapped to `DecimalType` as well.
COMP-3 packs two digits per byte with the sign in the low nibble of the last
byte. The ASCII flat files in `app/data/ASCII/` are DISPLAY-format conversions,
so COMP-3 encoding does not apply to these files.

### FILLER → StringType (retained but typically ignored)

COBOL `FILLER` fields are padding to reach the declared record length. They are
parsed as `StringType` and included in the schema for completeness. In practice,
they contain only spaces and can be dropped from downstream processing.

---

## Field-Name Normalization

COBOL names use hyphens (e.g., `ACCT-CURR-BAL`). These are converted to
underscores (`ACCT_CURR_BAL`) for PySpark column names, since hyphens are not
valid in most SQL/DataFrame column identifiers.

Copybook typos (e.g., `ACCT-EXPIRAION-DATE` instead of `EXPIRATION`) are
**preserved** in the generated column names and JSON schemas to maintain
traceability back to the original COBOL source.

---

## Record-Length Verification

Each data file was verified against its declared record length:

| File | Expected | Bytes/Line | Lines | Total Bytes | Status |
|---|---:|---:|---:|---:|---|
| `acctdata.txt` | 300 | 300 + LF | 50 | 15,050 | Verified |
| `custdata.txt` | 500 | 500 + LF | 50 | 25,050 | Verified |
| `carddata.txt` | 150 | 150 + LF | 50 | 7,550 | Verified |

---

## Sign Overpunch Handling

The ASCII data files use **EBCDIC-compatible trailing sign overpunch** encoding
for signed DISPLAY numerics. This is the standard COBOL behavior when
converting EBCDIC data to ASCII while preserving the sign convention.

The decoding logic is implemented in `pyspark_scripts/cobol_types.py` in the
`decode_signed_display()` function, which handles:
1. Positive overpunch characters (`{`, `A`–`I`)
2. Negative overpunch characters (`}`, `J`–`R`)
3. Plain digit fallback (treats unsigned trailing digits as positive)

---

## Generated Artifacts

```
pyspark_scripts/
  __init__.py
  cobol_types.py          # Shared COBOL→PySpark utilities
  parse_acctdata.py       # CVACT01Y.cpy → acctdata.txt parser
  parse_custdata.py       # CUSTREC.cpy  → custdata.txt parser
  parse_carddata.py       # CVACT02Y.cpy → carddata.txt parser

schemas/
  acctdata_schema.json    # JSON schema for ACCOUNT-RECORD
  custdata_schema.json    # JSON schema for CUSTOMER-RECORD
  carddata_schema.json    # JSON schema for CARD-RECORD

validation/
  validate_acctdata.txt   # Row-count + sample validation output
  validate_custdata.txt   # Row-count + sample validation output
  validate_carddata.txt   # Row-count + sample validation output
```

## Running the Parsers

```bash
# Requires Python 3.10+, PySpark 3.5+, Java 11+
pip install 'pyspark>=3.5,<4'

# Parse all three files
python pyspark_scripts/parse_acctdata.py
python pyspark_scripts/parse_custdata.py
python pyspark_scripts/parse_carddata.py

# Override default data path
python pyspark_scripts/parse_acctdata.py --data-path /path/to/acctdata.txt
```
