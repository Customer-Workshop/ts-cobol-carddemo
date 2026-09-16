package com.carddemo.data;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CobolFieldReaderTest {

    @Test
    void parsesPositiveAndNegativeOverpunchValues() {
        assertEquals(new BigDecimal("194.00"),
                CobolFieldReader.signedDecimal("00000001940{", 2));
        assertEquals(new BigDecimal("12.35"),
                CobolFieldReader.signedDecimal("0000000123E", 2));
        assertEquals(new BigDecimal("-12.36"),
                CobolFieldReader.signedDecimal("0000000123O", 2));
        assertEquals(new BigDecimal("-0.00"),
                CobolFieldReader.signedDecimal("0000000000}", 2));
    }

    @Test
    void parsesUnsignedAndTextFields() {
        assertEquals(42L, CobolFieldReader.unsignedLong("0000000042", 0, 10));
        assertEquals("hello", CobolFieldReader.text("hello     ", 0, 10));
        assertNull(CobolFieldReader.text("          ", 0, 10));
        assertNull(CobolFieldReader.signedDecimal("            ", 2));
    }

    @Test
    void rejectsMalformedNumericFields() {
        assertThrows(IllegalArgumentException.class,
                () -> CobolFieldReader.unsignedLong("00000X0042", 0, 10));
        assertThrows(IllegalArgumentException.class,
                () -> CobolFieldReader.unsignedLong("          ", 0, 10));
        assertThrows(IllegalArgumentException.class,
                () -> CobolFieldReader.signedDecimal("00000000X0A", 2));
    }

    @Test
    void splitsNewlineFreeFixedWidthRecords() {
        assertEquals(java.util.List.of("ABC", "DEF"),
                CobolFieldReader.splitRecords("ABCDEF", 3));
    }
}
