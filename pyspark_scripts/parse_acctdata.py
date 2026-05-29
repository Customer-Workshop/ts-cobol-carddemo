"""Parse app/data/ASCII/acctdata.txt using the CVACT01Y.cpy copybook layout.

Copybook: ACCOUNT-RECORD  (300 bytes, fixed-width)
Source:   app/cpy/CVACT01Y.cpy

Usage:
    spark-submit pyspark_scripts/parse_acctdata.py [--data-path <path>]
"""

import argparse
import json
import os
import sys
from decimal import Decimal
from pathlib import Path

from pyspark.sql import Row, SparkSession

sys.path.insert(0, str(Path(__file__).resolve().parent))
from cobol_types import (
    FieldSpec,
    build_struct,
    decode_signed_display,
    decode_unsigned_display,
)

# ---------------------------------------------------------------------------
# Field definitions derived from CVACT01Y.cpy
# Record length: 300 bytes
# ---------------------------------------------------------------------------
RECORD_LENGTH = 300

FIELDS: list[FieldSpec] = [
    FieldSpec("ACCT_ID",                "9(11)",     0,  11, 0,  False, "LongType"),
    FieldSpec("ACCT_ACTIVE_STATUS",     "X(01)",    11,   1, 0,  False, "StringType"),
    FieldSpec("ACCT_CURR_BAL",          "S9(10)V99",12,  12, 2,  True,  "DecimalType(12,2)"),
    FieldSpec("ACCT_CREDIT_LIMIT",      "S9(10)V99",24,  12, 2,  True,  "DecimalType(12,2)"),
    FieldSpec("ACCT_CASH_CREDIT_LIMIT", "S9(10)V99",36,  12, 2,  True,  "DecimalType(12,2)"),
    FieldSpec("ACCT_OPEN_DATE",         "X(10)",    48,  10, 0,  False, "StringType"),
    FieldSpec("ACCT_EXPIRAION_DATE",    "X(10)",    58,  10, 0,  False, "StringType"),
    FieldSpec("ACCT_REISSUE_DATE",      "X(10)",    68,  10, 0,  False, "StringType"),
    FieldSpec("ACCT_CURR_CYC_CREDIT",   "S9(10)V99",78,  12, 2,  True,  "DecimalType(12,2)"),
    FieldSpec("ACCT_CURR_CYC_DEBIT",    "S9(10)V99",90,  12, 2,  True,  "DecimalType(12,2)"),
    FieldSpec("ACCT_ADDR_ZIP",          "X(10)",   102,  10, 0,  False, "StringType"),
    FieldSpec("ACCT_GROUP_ID",          "X(10)",   112,  10, 0,  False, "StringType"),
    FieldSpec("FILLER",                 "X(178)",  122, 178, 0,  False, "StringType"),
]


def parse_line(line: str) -> Row:
    """Parse a single 300-byte line into a Row based on the copybook layout."""
    values = {}
    for f in FIELDS:
        raw = line[f.offset : f.offset + f.length]
        if f.pic.startswith("X"):
            values[f.name] = raw.rstrip()
        elif f.signed:
            values[f.name] = decode_signed_display(raw, f.scale)
        else:
            values[f.name] = decode_unsigned_display(raw)
    return Row(**values)


def main():
    parser = argparse.ArgumentParser(description="Parse CVACT01Y account data")
    parser.add_argument(
        "--data-path",
        default=os.path.join(
            os.path.dirname(__file__), "..", "app", "data", "ASCII", "acctdata.txt"
        ),
        help="Path to the acctdata.txt fixed-width file",
    )
    args = parser.parse_args()
    data_path = os.path.abspath(args.data_path)

    spark = SparkSession.builder.appName("ParseAcctData_CVACT01Y").getOrCreate()
    spark.sparkContext.setLogLevel("WARN")
    spark.sparkContext.addPyFile(
        str(Path(__file__).resolve().parent / "cobol_types.py")
    )

    raw_rdd = spark.sparkContext.textFile(data_path)
    row_rdd = raw_rdd.map(parse_line)
    schema = build_struct(FIELDS)
    df = spark.createDataFrame(row_rdd, schema)

    print("=" * 72)
    print("CVACT01Y.cpy → acctdata.txt  —  Parsed DataFrame")
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
