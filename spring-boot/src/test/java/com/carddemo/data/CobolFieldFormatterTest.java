package com.carddemo.data;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Overpunch-encoder tests: {@link CobolFieldFormatter#signedDecimal} must produce the
 * same zoned-decimal bytes {@link CobolFieldReader#signedDecimal} reads from the seed
 * exports (round-trip property).
 */
class CobolFieldFormatterTest {

    @Test
    void rendersPositiveValuesWithBraceOrLetterOverpunch() {
        assertEquals("00000001940{", CobolFieldFormatter.signedDecimal(new BigDecimal("194.00"), 10, 2));
        assertEquals("00000001940A", CobolFieldFormatter.signedDecimal(new BigDecimal("194.01"), 10, 2));
        assertEquals("00000001940I", CobolFieldFormatter.signedDecimal(new BigDecimal("194.09"), 10, 2));
        assertEquals("00000000000{", CobolFieldFormatter.signedDecimal(BigDecimal.ZERO.setScale(2), 10, 2));
    }

    @Test
    void rendersNegativeValuesWithJToROverpunch() {
        assertEquals("00000001940}", CobolFieldFormatter.signedDecimal(new BigDecimal("-194.00"), 10, 2));
        assertEquals("00000001940J", CobolFieldFormatter.signedDecimal(new BigDecimal("-194.01"), 10, 2));
        assertEquals("00000001940R", CobolFieldFormatter.signedDecimal(new BigDecimal("-194.09"), 10, 2));
    }

    @Test
    void truncatesHighOrderDigitsLikeACobolNumericMove() {
        assertEquals("23450{", CobolFieldFormatter.signedDecimal(new BigDecimal("12345.00"), 4, 2));
    }

    @Test
    void roundTripsThroughTheReader() {
        String encoded = CobolFieldFormatter.signedDecimal(new BigDecimal("-194.05"), 10, 2);
        assertEquals("00000001940N", encoded);
        assertEquals(new BigDecimal("-194.05"), CobolFieldReader.signedDecimal(encoded, 2));
    }

    @Test
    void padsAndTruncatesTextFields() {
        assertEquals("02108     ", CobolFieldFormatter.pad("02108", 10));
        assertEquals("          ", CobolFieldFormatter.pad(null, 10));
        assertEquals("ABC", CobolFieldFormatter.pad("ABCDEF", 3));
    }
}
