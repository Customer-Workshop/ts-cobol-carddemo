# COBOL Copybook to PySpark: Parsing & Type-Mapping Notes

## Overview

This directory contains PySpark scripts, JSON schemas, and validation outputs
for ingesting three fixed-width ASCII feed files produced by the CardDemo
mainframe application.  Each feed file's record layout is defined by a COBOL
copybook in `app/cpy/`.

| Copybook | Record | Bytes | Feed file | PySpark script |
|---|---|---|---|---|
| `CVACT01Y.cpy` | `ACCOUNT-RECORD` | 300 | `acctdata.txt` | `scripts/parse_acctdata.py` |
| `CUSTREC.cpy` | `CUSTOMER-RECORD` | 500 | `custdata.txt` | `scripts/parse_custdata.py` |
| `CVACT02Y.cpy` | `CARD-RECORD` | 150 | `carddata.txt` | `scripts/parse_carddata.py` |

---

## Type-Mapping Decisions

### 1. `PIC X(n)` &rarr; `StringType`

All alphanumeric COBOL fields (`USAGE DISPLAY`, the default) are mapped to
PySpark `StringType`.  Values are right-trimmed after extraction to remove
padding spaces.

### 2. `PIC 9(n)` (unsigned numeric, DISPLAY) &rarr; `LongType` / `IntegerType`

Unsigned numeric fields contain only the characters `0`-`9` in DISPLAY format.
They are safe to cast directly to integer types after trimming.

| Digits | PySpark type | Rationale |
|--------|-------------|-----------|
| n &le; 9 &ensp;(e.g. `PIC 9(03)`, `PIC 9(09)`) | `IntegerType` / `LongType` | Fits in 32-bit signed int, but `LongType` is used for IDs to avoid overflow when joining with 11-digit keys |
| n &ge; 10 &ensp;(e.g. `PIC 9(11)`) | `LongType` | Exceeds 32-bit signed int range |

**Special case &mdash; `CARD-NUM` (`PIC X(16)`):** Although the card number
contains only digits, the copybook declares it as alphanumeric (`PIC X`).
It is kept as `StringType` to preserve leading zeros, which are semantically
significant for card numbers.

### 3. `PIC S9(n)V99` (signed numeric with implied decimal, DISPLAY) &rarr; `DecimalType(n+4, 2)`

These fields use the COBOL **sign-overpunch** convention.  The sign is encoded
in the **last byte** of the field by replacing the trailing digit with a
letter:

| Last char | Digit | Sign |
|-----------|-------|------|
| `{` | 0 | + |
| `A`..`I` | 1..9 | + |
| `}` | 0 | &minus; |
| `J`..`R` | 1..9 | &minus; |

The `V` (implied decimal point) means the last two digits represent cents.
For example, `PIC S9(10)V99` occupies 12 bytes; the raw value
`00000001940{` decodes as **+000000019400** &rarr; **194.00**.

Decoding is performed by `utils/cobol_types.py:build_overpunch_expr()` which
produces a pure-Spark column expression (no Python UDF) for optimal
performance on distributed datasets.

The target PySpark type is `DecimalType(14, 2)` for `S9(10)V99`:
- **precision 14** = 10 integer digits + 2 decimal digits + 2 guard digits
- **scale 2** = two decimal places

### 4. `COMP-3` (Packed Decimal) &rarr; `DecimalType`

None of the three copybooks processed here use `COMP-3`.  If encountered in
other CardDemo copybooks, packed-decimal fields should be decoded from the
raw byte representation (two digits per byte, sign nibble in last half-byte)
before casting to `DecimalType`.  The EBCDIC feed files in `app/data/EBCDIC/`
may require this handling.

### 5. `COMP` (Binary) &rarr; `IntegerType` / `LongType`

Not present in the three copybooks processed here.  Binary fields (2- or
4-byte big-endian integers) would be decoded with struct-unpack logic and
mapped to `IntegerType` (2-byte / 4-byte) or `LongType` (8-byte).

### 6. `FILLER` &rarr; `StringType` (extracted but ignorable)

FILLER fields contain padding spaces and carry no business meaning.  They
are included in the parsed DataFrame for record-length verification but can
be dropped by downstream consumers.

### 7. Date fields (`PIC X(10)`)

Date fields like `ACCT-OPEN-DATE` and `CUST-DOB-YYYYMMDD` are stored as
`YYYY-MM-DD` formatted strings.  They are kept as `StringType` in the
initial parse stage.  Downstream pipelines should cast them to `DateType`
after validating format consistency.

---

## Feed File Characteristics

| File | Records | Bytes/record | Line terminator | Encoding |
|---|---|---|---|---|
| `acctdata.txt` | 50 | 300 (+LF) | `\n` | ASCII |
| `custdata.txt` | 50 | 500 (+LF) | `\n` | ASCII |
| `carddata.txt` | 50 | 150 (+LF) | `\n` | ASCII |

The feed files in `app/data/ASCII/` have already been converted from EBCDIC
to ASCII.  Sign-overpunch characters are preserved in their ASCII-mapped form
(see table above).

---

## Running the Scripts

```bash
# From the repository root
cd pyspark_copybook_parsers

spark-submit --master "local[*]" scripts/parse_acctdata.py
spark-submit --master "local[*]" scripts/parse_custdata.py
spark-submit --master "local[*]" scripts/parse_carddata.py
```

Each script:
1. Reads the corresponding fixed-width file as single-column text
2. Extracts fields using `F.substring()` with 1-based offsets
3. Applies type conversions (sign-overpunch decoding for signed numerics)
4. Prints the schema, row count, and first 5 rows to stdout
5. Writes a JSON validation report to `validation/`

---

## Validation

Each validation JSON file (`validation/*_validation.json`) contains:

| Key | Description |
|---|---|
| `raw_line_count` | Number of lines read from the feed file |
| `parsed_row_count` | Number of rows in the parsed DataFrame |
| `row_count_match` | Boolean confirming counts are equal |
| `schema_fields` | List of column names in the parsed DataFrame |
| `sample_rows` | First 5 rows as key-value dictionaries |

---

## Project Structure

```
pyspark_copybook_parsers/
├── COPYBOOK_PARSING_NOTES.md          ← this file
├── schemas/
│   ├── CVACT01Y_account_schema.json   ← ACCOUNT-RECORD field definitions
│   ├── CUSTREC_customer_schema.json   ← CUSTOMER-RECORD field definitions
│   └── CVACT02Y_card_schema.json      ← CARD-RECORD field definitions
├── scripts/
│   ├── parse_acctdata.py              ← PySpark parser for acctdata.txt
│   ├── parse_custdata.py              ← PySpark parser for custdata.txt
│   └── parse_carddata.py              ← PySpark parser for carddata.txt
├── utils/
│   ├── __init__.py
│   └── cobol_types.py                 ← sign-overpunch decoder & Spark exprs
└── validation/
    ├── acctdata_validation.json       ← generated at runtime
    ├── custdata_validation.json       ← generated at runtime
    └── carddata_validation.json       ← generated at runtime
```
