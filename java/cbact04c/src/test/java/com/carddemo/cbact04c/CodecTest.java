package com.carddemo.cbact04c;

import com.carddemo.cbact04c.domain.Records.Account;
import com.carddemo.cbact04c.io.RecordCodecs;
import com.carddemo.cbact04c.util.ZonedDecimal;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CodecTest {

    @Test
    void realTransactionCategoryRecordRoundTripsAtCopybookOffsets() throws IOException {
        String line = firstLine("tcatbal.txt");
        assertEquals(line, RecordCodecs.encodeTranCat(RecordCodecs.decodeTranCat(line)));
        assertEquals("00000000001", RecordCodecs.decodeTranCat(line).acctId());
        assertEquals("01", RecordCodecs.decodeTranCat(line).typeCd());
        assertEquals("0001", RecordCodecs.decodeTranCat(line).catCd());
    }

    @Test
    void realDisclosureGroupRecordRoundTripsAtCopybookOffsets() throws IOException {
        String line = firstLine("discgrp.txt");
        assertEquals(line, RecordCodecs.encodeDiscGroup(RecordCodecs.decodeDiscGroup(line)));
        assertEquals(new BigDecimal("15.00"), RecordCodecs.decodeDiscGroup(line).rate());
    }

    @Test
    void realAccountRecordRoundTripsAllThreeHundredBytes() throws IOException {
        String line = firstLine("acctdata.txt");
        Account account = RecordCodecs.decodeAccount(line);
        assertEquals(300, line.length());
        assertEquals(line, RecordCodecs.encodeAccount(account));
    }

    @Test
    void shortCardXrefIsPaddedAndRoundTripsAtTheDeclaredFields() throws IOException {
        String line = firstLine("cardxref.txt");
        assertEquals(36, line.length());
        assertEquals(50, RecordCodecs.decodeXref(line).raw().length());
        assertEquals(
                RecordCodecs.decodeXref(line).raw(),
                RecordCodecs.encodeXref(RecordCodecs.decodeXref(line)));
    }

    @Test
    void everyOverpunchCharacterParses() {
        assertEquals(BigDecimal.ZERO.setScale(2), ZonedDecimal.parse("0000000000{"));
        for (char overpunch = 'A'; overpunch <= 'I'; overpunch++) {
            assertEquals(
                    BigDecimal.valueOf(overpunch - 'A' + 1, 2),
                    ZonedDecimal.parse("0000000000" + overpunch));
        }
        assertEquals(BigDecimal.ZERO.setScale(2).negate(),
                ZonedDecimal.parse("0000000000}"));
        for (char overpunch = 'J'; overpunch <= 'R'; overpunch++) {
            assertEquals(
                    BigDecimal.valueOf(-(overpunch - 'J' + 1), 2),
                    ZonedDecimal.parse("0000000000" + overpunch));
        }
        assertEquals(new BigDecimal("12.34"),
                ZonedDecimal.parse("0000000001234"));
    }

    @Test
    void negativeValuesFormatWithTrailingOverpunch() {
        assertEquals("00000000123M", ZonedDecimal.format(new BigDecimal("-12.34"), 10));
        assertEquals("00000000000R", ZonedDecimal.format(new BigDecimal("-0.09"), 10));
    }

    @Test
    void valuesExceedingPicDigitsAreRejectedBeforeRecordCorruption() {
        assertThrows(
                ArithmeticException.class,
                () -> ZonedDecimal.format(new BigDecimal("10000000000.00"), 10));
    }

    private String firstLine(String fileName) throws IOException {
        return Files.readAllLines(
                        Path.of("src", "test", "resources", "fixtures", fileName))
                .get(0);
    }
}
