"""
PySpark script to parse app/data/ASCII/custdata.txt using the record layout
defined in app/cpy/CUSTREC.cpy (CUSTOMER-RECORD, 500 bytes per record).

Usage:
    spark-submit --master "local[*]" scripts/parse_custdata.py [data_dir]
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
INPUT_FILE = os.path.join(DATA_DIR, "custdata.txt")
VALIDATION_FILE = os.path.join(PROJECT_DIR, "validation", "custdata_validation.json")

RECORD_LENGTH = 500

# Field layout: (name, 1-based start, length, type_tag)
FIELD_LAYOUT = [
    ("CUST_ID",                  1,   9, "long"),
    ("CUST_FIRST_NAME",         10,  25, "string"),
    ("CUST_MIDDLE_NAME",        35,  25, "string"),
    ("CUST_LAST_NAME",          60,  25, "string"),
    ("CUST_ADDR_LINE_1",        85,  50, "string"),
    ("CUST_ADDR_LINE_2",       135,  50, "string"),
    ("CUST_ADDR_LINE_3",       185,  50, "string"),
    ("CUST_ADDR_STATE_CD",     235,   2, "string"),
    ("CUST_ADDR_COUNTRY_CD",   237,   3, "string"),
    ("CUST_ADDR_ZIP",          240,  10, "string"),
    ("CUST_PHONE_NUM_1",       250,  15, "string"),
    ("CUST_PHONE_NUM_2",       265,  15, "string"),
    ("CUST_SSN",               280,   9, "long"),
    ("CUST_GOVT_ISSUED_ID",    289,  20, "string"),
    ("CUST_DOB_YYYYMMDD",      309,  10, "string"),
    ("CUST_EFT_ACCOUNT_ID",    319,  10, "string"),
    ("CUST_PRI_CARD_HOLDER_IND", 329, 1, "string"),
    ("CUST_FICO_CREDIT_SCORE", 330,   3, "int"),
    ("FILLER",                 333, 168, "string"),
]


def main():
    spark = SparkSession.builder \
        .appName("CUSTREC_CustomerParser") \
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
    print("\nFirst 5 rows (selected columns):")
    parsed.select(
        "CUST_ID", "CUST_FIRST_NAME", "CUST_LAST_NAME",
        "CUST_ADDR_STATE_CD", "CUST_SSN", "CUST_FICO_CREDIT_SCORE",
    ).show(5, truncate=False)

    # ---- Validation output ------------------------------------------------
    sample_rows = [row.asDict() for row in parsed.head(5)]
    for row in sample_rows:
        for k, v in row.items():
            if hasattr(v, "as_tuple"):
                row[k] = str(v)

    validation = {
        "source_file": INPUT_FILE,
        "copybook": "CUSTREC.cpy",
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
