package com.carddemo.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CobolFieldReaderTest {

    /** app/data/ASCII/acctdata.txt:1 (first 60 bytes). */
    private static final String ACCT_ROW_1 =
            "00000000001Y00000001940{00000020200{00000010200{2014-11-202025-05-202025-05-20";

    @Test
    @DisplayName("zoned overpunch: '{' is +0 in the last digit (02_conventions.md §1)")
    void overpunchPositiveZero() {
        assertEquals(new BigDecimal("194.00"), CobolFieldReader.signedDecimal(ACCT_ROW_1, 12, 12, 2));
        assertEquals(new BigDecimal("2020.00"), CobolFieldReader.signedDecimal(ACCT_ROW_1, 24, 12, 2));
    }

    @Test
    @DisplayName("zoned overpunch: A-I positive 1-9, J-R negative 1-9, '}' negative zero")
    void overpunchLetters() {
        assertEquals(new BigDecimal("1.21"), CobolFieldReader.signedDecimal("00012A", 2));
        assertEquals(new BigDecimal("-1.21"), CobolFieldReader.signedDecimal("00012J", 2));
        assertEquals(new BigDecimal("-1.29"), CobolFieldReader.signedDecimal("00012R", 2));
        assertEquals(new BigDecimal("-1.20"), CobolFieldReader.signedDecimal("00012}", 2));
        assertEquals(new BigDecimal("1.20"), CobolFieldReader.signedDecimal("000120", 2));
        assertThrows(IllegalArgumentException.class, () -> CobolFieldReader.signedDecimal("00012Z", 2));
    }

    @Test
    void textTrimsTrailingSpacesAndReturnsNullWhenBlank() {
        assertEquals("Y", CobolFieldReader.text(ACCT_ROW_1, 11, 1));
        assertNull(CobolFieldReader.text("          ", 0, 10));
        assertEquals("2014-11-20", CobolFieldReader.rawText(ACCT_ROW_1, 48, 10));
        assertEquals("ab  ", CobolFieldReader.rawText("ab", 0, 4));
    }

    @Test
    void numericFields() {
        assertEquals(1L, CobolFieldReader.requiredUnsignedLong(ACCT_ROW_1, 0, 11, "acctdata.txt", 1));
        assertNull(CobolFieldReader.optionalUnsignedLong("         ", 0, 9));
        assertThrows(ImportFormatException.class,
                () -> CobolFieldReader.requiredUnsignedLong("ABC", 0, 3, "acctdata.txt", 7));
        assertThrows(ImportFormatException.class,
                () -> CobolFieldReader.requiredText("   ", 0, 3, "cardxref.txt", 3));
    }

    @Test
    void splitRecordsPadsShortTail() {
        List<String> records = CobolFieldReader.splitRecords("AAAABBBBC", 4);
        assertEquals(List.of("AAAA", "BBBB", "C   "), records);
    }
}
