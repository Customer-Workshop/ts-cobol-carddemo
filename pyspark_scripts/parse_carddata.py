"""Parse app/data/ASCII/carddata.txt using the CVACT02Y.cpy copybook layout.

Copybook: CARD-RECORD  (150 bytes, fixed-width)
Source:   app/cpy/CVACT02Y.cpy

Usage:
    spark-submit pyspark_scripts/parse_carddata.py [--data-path <path>]
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
# Field definitions derived from CVACT02Y.cpy
# Record length: 150 bytes
# ---------------------------------------------------------------------------
RECORD_LENGTH = 150

FIELDS: list[FieldSpec] = [
    FieldSpec("CARD_NUM",            "X(16)",   0, 16, 0, False, "StringType"),
    FieldSpec("CARD_ACCT_ID",        "9(11)",  16, 11, 0, False, "LongType"),
    FieldSpec("CARD_CVV_CD",         "9(03)",  27,  3, 0, False, "IntegerType"),
    FieldSpec("CARD_EMBOSSED_NAME",  "X(50)",  30, 50, 0, False, "StringType"),
    FieldSpec("CARD_EXPIRAION_DATE", "X(10)",  80, 10, 0, False, "StringType"),
    FieldSpec("CARD_ACTIVE_STATUS",  "X(01)",  90,  1, 0, False, "StringType"),
    FieldSpec("FILLER",              "X(59)",  91, 59, 0, False, "StringType"),
]


def parse_line(line: str) -> Row:
    """Parse a single 150-byte line into a Row based on the copybook layout."""
    values = {}
    for f in FIELDS:
        raw = line[f.offset : f.offset + f.length]
        if f.pic.startswith("X"):
            values[f.name] = raw.rstrip()
        else:
            values[f.name] = decode_unsigned_display(raw)
    return Row(**values)


def main():
    parser = argparse.ArgumentParser(description="Parse CVACT02Y card data")
    parser.add_argument(
        "--data-path",
        default=os.path.join(
            os.path.dirname(__file__), "..", "app", "data", "ASCII", "carddata.txt"
        ),
        help="Path to the carddata.txt fixed-width file",
    )
    args = parser.parse_args()
    data_path = os.path.abspath(args.data_path)

    spark = SparkSession.builder.appName("ParseCardData_CVACT02Y").getOrCreate()
    spark.sparkContext.setLogLevel("WARN")
    spark.sparkContext.addPyFile(
        str(Path(__file__).resolve().parent / "cobol_types.py")
    )

    raw_rdd = spark.sparkContext.textFile(data_path)
    row_rdd = raw_rdd.map(parse_line)
    schema = build_struct(FIELDS)
    df = spark.createDataFrame(row_rdd, schema)

    print("=" * 72)
    print("CVACT02Y.cpy → carddata.txt  —  Parsed DataFrame")
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
