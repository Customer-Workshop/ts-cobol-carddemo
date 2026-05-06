"""
PySpark script to parse app/data/ASCII/carddata.txt using the record layout
defined in app/cpy/CVACT02Y.cpy (CARD-RECORD, 150 bytes per record).

Usage:
    spark-submit --master "local[*]" scripts/parse_carddata.py [data_dir]
"""

import json
import os
import sys

from pyspark.sql import SparkSession
from pyspark.sql import functions as F
from pyspark.sql.types import IntegerType, LongType

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_DIR = os.path.dirname(SCRIPT_DIR)
REPO_DIR = os.path.dirname(PROJECT_DIR)
sys.path.insert(0, PROJECT_DIR)

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
DATA_DIR = sys.argv[1] if len(sys.argv) > 1 else os.path.join(REPO_DIR, "app", "data", "ASCII")
INPUT_FILE = os.path.join(DATA_DIR, "carddata.txt")
VALIDATION_FILE = os.path.join(PROJECT_DIR, "validation", "carddata_validation.json")

RECORD_LENGTH = 150

# Field layout: (name, 1-based start, length, type_tag)
FIELD_LAYOUT = [
    ("CARD_NUM",              1,  16, "string"),
    ("CARD_ACCT_ID",         17,  11, "long"),
    ("CARD_CVV_CD",          28,   3, "int"),
    ("CARD_EMBOSSED_NAME",   31,  50, "string"),
    ("CARD_EXPIRAION_DATE",  81,  10, "string"),
    ("CARD_ACTIVE_STATUS",   91,   1, "string"),
    ("FILLER",               92,  59, "string"),
]


def main():
    spark = SparkSession.builder \
        .appName("CVACT02Y_CardParser") \
        .getOrCreate()
    spark.sparkContext.setLogLevel("WARN")

    raw_df = spark.read.text(INPUT_FILE)
    raw_count = raw_df.count()
    print(f"\n{'='*60}")
    print(f"  Raw line count : {raw_count}")
    print(f"  Expected RECLN : {RECORD_LENGTH}")
    print(f"{'='*60}\n")

    parsed = raw_df
    for name, start, length, type_tag in FIELD_LAYOUT:
        col_expr = F.trim(F.substring("value", start, length))
        if type_tag == "long":
            col_expr = col_expr.cast(LongType())
        elif type_tag == "int":
            col_expr = col_expr.cast(IntegerType())
        parsed = parsed.withColumn(name, col_expr)

    parsed = parsed.drop("value")

    print("Schema:")
    parsed.printSchema()
    print(f"\nParsed row count: {parsed.count()}")
    print("\nFirst 5 rows:")
    parsed.select(
        "CARD_NUM", "CARD_ACCT_ID", "CARD_CVV_CD",
        "CARD_EMBOSSED_NAME", "CARD_EXPIRAION_DATE", "CARD_ACTIVE_STATUS",
    ).show(5, truncate=False)

    # ---- Validation output ------------------------------------------------
    sample_rows = [row.asDict() for row in parsed.head(5)]
    for row in sample_rows:
        for k, v in row.items():
            if hasattr(v, "as_tuple"):
                row[k] = str(v)

    validation = {
        "source_file": INPUT_FILE,
        "copybook": "CVACT02Y.cpy",
        "record_length": RECORD_LENGTH,
        "raw_line_count": raw_count,
        "parsed_row_count": parsed.count(),
        "row_count_match": raw_count == parsed.count(),
        "schema_fields": [f.name for f in parsed.schema.fields],
        "sample_rows": sample_rows,
    }

    os.makedirs(os.path.dirname(VALIDATION_FILE), exist_ok=True)
    with open(VALIDATION_FILE, "w") as fh:
        json.dump(validation, fh, indent=2, default=str)
    print(f"\nValidation written to {VALIDATION_FILE}")

    spark.stop()


if __name__ == "__main__":
    main()
