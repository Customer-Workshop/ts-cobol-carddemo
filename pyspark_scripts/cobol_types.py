"""Shared utilities for parsing COBOL fixed-width files with PySpark.

Handles:
- EBCDIC-to-ASCII sign overpunch decoding for PIC S9(n)Vn fields
- Fixed-width field extraction from raw text lines
- Conversion of COBOL PIC clauses to PySpark types
"""

from decimal import Decimal
from typing import NamedTuple

from pyspark.sql.types import (
    DecimalType,
    IntegerType,
    LongType,
    StringType,
    StructField,
    StructType,
)

# ---------------------------------------------------------------------------
# EBCDIC-to-ASCII sign overpunch maps
#   Positive: { = 0, A–I = 1–9
#   Negative: } = 0, J–R = 1–9
# ---------------------------------------------------------------------------
_POSITIVE_OVERPUNCH = {"{": 0, "A": 1, "B": 2, "C": 3, "D": 4,
                       "E": 5, "F": 6, "G": 7, "H": 8, "I": 9}
_NEGATIVE_OVERPUNCH = {"}": 0, "J": 1, "K": 2, "L": 3, "M": 4,
                       "N": 5, "O": 6, "P": 7, "Q": 8, "R": 9}


class FieldSpec(NamedTuple):
    """Metadata for a single COBOL copybook field."""
    name: str
    pic: str
    offset: int
    length: int
    scale: int          # decimal places (V99 → 2)
    signed: bool
    pyspark_type: str   # human-readable type name for JSON schema


def decode_signed_display(raw: str, scale: int) -> Decimal:
    """Decode a COBOL DISPLAY signed numeric (sign overpunch on last byte).

    Parameters
    ----------
    raw : str
        The fixed-width substring exactly as read from the ASCII file.
    scale : int
        Number of implied decimal places (from the ``V`` in PIC S9(m)V9(n)).

    Returns
    -------
    Decimal
        The decoded numeric value.

    Raises
    ------
    ValueError
        If the trailing character is not a valid overpunch or digit.
    """
    if not raw or raw.isspace():
        return Decimal(0)

    body = raw[:-1]
    trail = raw[-1]

    if trail in _POSITIVE_OVERPUNCH:
        sign = 1
        last_digit = _POSITIVE_OVERPUNCH[trail]
    elif trail in _NEGATIVE_OVERPUNCH:
        sign = -1
        last_digit = _NEGATIVE_OVERPUNCH[trail]
    elif trail.isdigit():
        sign = 1
        last_digit = int(trail)
    else:
        raise ValueError(f"Invalid overpunch character: {trail!r} in {raw!r}")

    digits = body + str(last_digit)
    int_val = int(digits)
    result = Decimal(int_val * sign)

    if scale > 0:
        result = result / (Decimal(10) ** scale)

    return result


def decode_unsigned_display(raw: str) -> int:
    """Decode a COBOL DISPLAY unsigned numeric (PIC 9(n))."""
    stripped = raw.strip()
    if not stripped:
        return 0
    return int(stripped)


def pic_to_pyspark_type(pic: str, signed: bool, scale: int, int_digits: int):
    """Map a COBOL PIC clause to a PySpark DataType instance."""
    if pic.startswith("X"):
        return StringType()
    if scale > 0:
        precision = int_digits + scale
        return DecimalType(precision, scale)
    total_digits = int_digits + scale
    if total_digits <= 9:
        return IntegerType()
    return LongType()


def build_struct(fields: list[FieldSpec]) -> StructType:
    """Build a PySpark StructType from a list of FieldSpec entries."""
    spark_fields = []
    for f in fields:
        if f.pic.startswith("X"):
            spark_fields.append(StructField(f.name, StringType(), True))
        elif f.scale > 0:
            int_digits = f.length - f.scale
            spark_fields.append(
                StructField(f.name, DecimalType(int_digits + f.scale, f.scale), True)
            )
        else:
            if f.length <= 9:
                spark_fields.append(StructField(f.name, IntegerType(), True))
            else:
                spark_fields.append(StructField(f.name, LongType(), True))
    return StructType(spark_fields)
