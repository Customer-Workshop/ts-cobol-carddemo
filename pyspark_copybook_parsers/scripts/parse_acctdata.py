"""
PySpark script to parse app/data/ASCII/acctdata.txt using the record layout
defined in app/cpy/CVACT01Y.cpy (ACCOUNT-RECORD, 300 bytes per record).

Signed numeric fields use COBOL DISPLAY format with EBCDIC sign-overpunch
in the last byte.  The helper in utils/cobol_types.py decodes them.

Usage:
    spark-submit --master "local[*]" scripts/parse_acctdata.py [data_dir]
"""

import json
import os
import sys

from pyspark.sql import SparkSession
from pyspark.sql import functions as F
from pyspark.sql.types import LongType, StringType

# Allow running from the repo root or from inside pyspark_copybook_parsers/
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_DIR = os.path.dirname(SCRIPT_DIR)
REPO_DIR = os.path.dirname(PROJECT_DIR)
sys.path.insert(0, PROJECT_DIR)

from utils.cobol_types import build_overpunch_expr

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
DATA_DIR = sys.argv[1] if len(sys.argv) > 1 else os.path.join(REPO_DIR, "app", "data", "ASCII")
INPUT_FILE = os.path.join(DATA_DIR, "acctdata.txt")
SCHEMA_FILE = os.path.join(PROJECT_DIR, "schemas", "CVACT01Y_account_schema.json")
VALIDATION_FILE = os.path.join(PROJECT_DIR, "validation", "acctdata_validation.json")

RECORD_LENGTH = 300

# Field layout: (name, 1-based start, length, type_tag)
# type_tag: "string", "long", "signed_display_2" (S9(n)V99 with scale=2)
FIELD_LAYOUT = [
    ("ACCT_ID",                1,   11, "long"),
    ("ACCT_ACTIVE_STATUS",    12,    1, "string"),
    ("ACCT_CURR_BAL",         13,   12, "signed_display_2"),
    ("ACCT_CREDIT_LIMIT",     25,   12, "signed_display_2"),
    ("ACCT_CASH_CREDIT_LIMIT",37,   12, "signed_display_2"),
    ("ACCT_OPEN_DATE",        49,   10, "string"),
    ("ACCT_EXPIRAION_DATE",   59,   10, "string"),
    ("ACCT_REISSUE_DATE",     69,   10, "string"),
    ("ACCT_CURR_CYC_CREDIT",  79,  12, "signed_display_2"),
    ("ACCT_CURR_CYC_DEBIT",   91,  12, "signed_display_2"),
    ("ACCT_ADDR_ZIP",        103,   10, "string"),
    ("ACCT_GROUP_ID",        113,   10, "string"),
    ("FILLER",               123,  178, "string"),
]


def main():
    spark = SparkSession.builder \
        .appName("CVACT01Y_AccountParser") \
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
        elif type_tag == "signed_display_2":
            raw_col = F.substring("value", start, length)
            col_expr = build_overpunch_expr(raw_col, scale=2)
        parsed = parsed.withColumn(name, col_expr)

    parsed = parsed.drop("value")

    print("Schema:")
    parsed.printSchema()
    print(f"\nParsed row count: {parsed.count()}")
    print("\nFirst 5 rows:")
    parsed.show(5, truncate=False)

    # ---- Validation output ------------------------------------------------
    sample_rows = [row.asDict() for row in parsed.head(5)]
    for row in sample_rows:
        for k, v in row.items():
            if hasattr(v, "as_tuple"):
                row[k] = str(v)

    validation = {
        "source_file": INPUT_FILE,
        "copybook": "CVACT01Y.cpy",
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
