"""Parse app/data/ASCII/custdata.txt using the CUSTREC.cpy copybook layout.

Copybook: CUSTOMER-RECORD  (500 bytes, fixed-width)
Source:   app/cpy/CUSTREC.cpy

Usage:
    spark-submit pyspark_scripts/parse_custdata.py [--data-path <path>]
"""

import argparse
import json
import os
import sys
from pathlib import Path

from pyspark.sql import Row, SparkSession

sys.path.insert(0, str(Path(__file__).resolve().parent))
from cobol_types import (
    FieldSpec,
    build_struct,
    decode_unsigned_display,
)

# ---------------------------------------------------------------------------
# Field definitions derived from CUSTREC.cpy
# Record length: 500 bytes
# ---------------------------------------------------------------------------
RECORD_LENGTH = 500

FIELDS: list[FieldSpec] = [
    FieldSpec("CUST_ID",                 "9(09)",   0,   9, 0, False, "IntegerType"),
    FieldSpec("CUST_FIRST_NAME",         "X(25)",   9,  25, 0, False, "StringType"),
    FieldSpec("CUST_MIDDLE_NAME",        "X(25)",  34,  25, 0, False, "StringType"),
    FieldSpec("CUST_LAST_NAME",          "X(25)",  59,  25, 0, False, "StringType"),
    FieldSpec("CUST_ADDR_LINE_1",        "X(50)",  84,  50, 0, False, "StringType"),
    FieldSpec("CUST_ADDR_LINE_2",        "X(50)", 134,  50, 0, False, "StringType"),
    FieldSpec("CUST_ADDR_LINE_3",        "X(50)", 184,  50, 0, False, "StringType"),
    FieldSpec("CUST_ADDR_STATE_CD",      "X(02)", 234,   2, 0, False, "StringType"),
    FieldSpec("CUST_ADDR_COUNTRY_CD",    "X(03)", 236,   3, 0, False, "StringType"),
    FieldSpec("CUST_ADDR_ZIP",           "X(10)", 239,  10, 0, False, "StringType"),
    FieldSpec("CUST_PHONE_NUM_1",        "X(15)", 249,  15, 0, False, "StringType"),
    FieldSpec("CUST_PHONE_NUM_2",        "X(15)", 264,  15, 0, False, "StringType"),
    FieldSpec("CUST_SSN",                "9(09)", 279,   9, 0, False, "IntegerType"),
    FieldSpec("CUST_GOVT_ISSUED_ID",     "X(20)", 288,  20, 0, False, "StringType"),
    FieldSpec("CUST_DOB_YYYYMMDD",       "X(10)", 308,  10, 0, False, "StringType"),
    FieldSpec("CUST_EFT_ACCOUNT_ID",     "X(10)", 318,  10, 0, False, "StringType"),
    FieldSpec("CUST_PRI_CARD_HOLDER_IND","X(01)", 328,   1, 0, False, "StringType"),
    FieldSpec("CUST_FICO_CREDIT_SCORE",  "9(03)", 329,   3, 0, False, "IntegerType"),
    FieldSpec("FILLER",                  "X(168)",332, 168, 0, False, "StringType"),
]


def parse_line(line: str) -> Row:
    """Parse a single 500-byte line into a Row based on the copybook layout."""
    values = {}
    for f in FIELDS:
        raw = line[f.offset : f.offset + f.length]
        if f.pic.startswith("X"):
            values[f.name] = raw.rstrip()
        else:
            values[f.name] = decode_unsigned_display(raw)
    return Row(**values)


def main():
    parser = argparse.ArgumentParser(description="Parse CUSTREC customer data")
    parser.add_argument(
        "--data-path",
        default=os.path.join(
            os.path.dirname(__file__), "..", "app", "data", "ASCII", "custdata.txt"
        ),
        help="Path to the custdata.txt fixed-width file",
    )
    args = parser.parse_args()
    data_path = os.path.abspath(args.data_path)

    spark = SparkSession.builder.appName("ParseCustData_CUSTREC").getOrCreate()
    spark.sparkContext.setLogLevel("WARN")
    spark.sparkContext.addPyFile(
        str(Path(__file__).resolve().parent / "cobol_types.py")
    )

    raw_rdd = spark.sparkContext.textFile(data_path)
    row_rdd = raw_rdd.map(parse_line)
    schema = build_struct(FIELDS)
    df = spark.createDataFrame(row_rdd, schema)

    print("=" * 72)
    print("CUSTREC.cpy → custdata.txt  —  Parsed DataFrame")
    print("=" * 72)
    df.printSchema()
    df.show(10, truncate=40)
    print(f"Total rows: {df.count()}")

    # ── Validation ────────────────────────────────────────────────────────
    raw_line_count = raw_rdd.count()
    df_row_count = df.count()
    print("\n── Validation ──")
    print(f"  Raw line count : {raw_line_count}")
    print(f"  DataFrame rows : {df_row_count}")
    print(f"  Match          : {'YES' if raw_line_count == df_row_count else 'NO'}")

    first_row = df.first()
    if first_row:
        print("\n  Sample row (first):")
        for f in FIELDS:
            if f.name == "FILLER":
                continue
            print(f"    {f.name:30s} = {getattr(first_row, f.name)!r}")

    spark.stop()


if __name__ == "__main__":
    main()
