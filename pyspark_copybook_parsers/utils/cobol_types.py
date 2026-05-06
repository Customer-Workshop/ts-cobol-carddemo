"""
Shared utilities for converting COBOL DISPLAY-format numeric fields
(including sign-overpunch encoding) into Python/PySpark-friendly values.
"""

from decimal import Decimal
from pyspark.sql import Column
from pyspark.sql import functions as F
from pyspark.sql.types import DecimalType, LongType, IntegerType

# ---------------------------------------------------------------------------
# EBCDIC sign-overpunch tables (ASCII representation)
# In COBOL DISPLAY signed numerics the sign is embedded in the last byte.
# When the mainframe data is converted to ASCII the last character encodes
# both the digit value and the sign.
# ---------------------------------------------------------------------------
POSITIVE_OVERPUNCH = {
    "{": 0, "A": 1, "B": 2, "C": 3, "D": 4,
    "E": 5, "F": 6, "G": 7, "H": 8, "I": 9,
}

NEGATIVE_OVERPUNCH = {
    "}": 0, "J": 1, "K": 2, "L": 3, "M": 4,
    "N": 5, "O": 6, "P": 7, "Q": 8, "R": 9,
}


def decode_sign_overpunch(raw: str) -> str:
    """Return a plain numeric string with explicit sign prefix.

    >>> decode_sign_overpunch("00000001940{")
    '+000000019400'
    >>> decode_sign_overpunch("00000001940R")
    '-000000019409'
    """
    if not raw or raw.strip() == "":
        return None
    last = raw[-1]
    body = raw[:-1]
    if last in POSITIVE_OVERPUNCH:
        return f"+{body}{POSITIVE_OVERPUNCH[last]}"
    if last in NEGATIVE_OVERPUNCH:
        return f"-{body}{NEGATIVE_OVERPUNCH[last]}"
    if last.isdigit():
        return f"+{raw}"
    return None


def signed_display_to_decimal(raw: str, scale: int) -> Decimal:
    """Convert a COBOL DISPLAY signed numeric string to a Python Decimal.

    *scale* is the number of implied decimal places (V99 → scale=2).
    """
    decoded = decode_sign_overpunch(raw)
    if decoded is None:
        return None
    sign = decoded[0]
    digits = decoded[1:]
    if scale > 0:
        integer_part = digits[:-scale]
        decimal_part = digits[-scale:]
        return Decimal(f"{sign}{integer_part}.{decimal_part}")
    return Decimal(f"{sign}{digits}")


# ---------------------------------------------------------------------------
# PySpark UDF-free column expressions
# ---------------------------------------------------------------------------

def build_overpunch_expr(col: Column, scale: int) -> Column:
    """Build a pure-Spark SQL expression that decodes a sign-overpunch column.

    Returns a DecimalType column with the given *scale*.
    """
    last_char = F.substring(col, -1, 1)
    body = F.substring(col, 1, F.length(col) - 1)

    digit_expr = last_char
    sign_expr = F.lit(1)

    for ch, val in POSITIVE_OVERPUNCH.items():
        digit_expr = F.when(last_char == ch, F.lit(str(val))).otherwise(digit_expr)
        sign_expr = F.when(last_char == ch, F.lit(1)).otherwise(sign_expr)
    for ch, val in NEGATIVE_OVERPUNCH.items():
        digit_expr = F.when(last_char == ch, F.lit(str(val))).otherwise(digit_expr)
        sign_expr = F.when(last_char == ch, F.lit(-1)).otherwise(sign_expr)

    full_digits = F.concat(body, digit_expr)

    if scale > 0:
        integer_part = F.substring(full_digits, 1, F.length(full_digits) - scale)
        decimal_part = F.substring(full_digits, F.length(full_digits) - scale + 1, scale)
        numeric_str = F.concat(integer_part, F.lit("."), decimal_part)
    else:
        numeric_str = full_digits

    return (numeric_str.cast(DecimalType(12 + scale, scale)) * sign_expr).cast(
        DecimalType(12 + scale, scale)
    )
