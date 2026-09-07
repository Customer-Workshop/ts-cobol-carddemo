package com.carddemo.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The edited pictures of map CACTVWA, derived by hand from the BMS clauses and the COBOL statements
 * (app/bms/COACTVW.bms:120-195, COACTVWC.cbl:471-507) — never from the formatter's own output.
 */
class CobolPictureTest {

    @Test
    @DisplayName("FR-16 — +ZZZ,ZZZ,ZZZ.99 is 15 bytes: fixed sign, zero-suppressed integer part, blank separators inside the suppressed run")
    void moneyPicture() {
        assertEquals("+        284.00", CobolPicture.money(new BigDecimal("284.00")));
        assertEquals("+      5,572.00", CobolPicture.money(new BigDecimal("5572.00")));
        assertEquals("+      2,075.00", CobolPicture.money(new BigDecimal("2075.00")));
        assertEquals("+      1,000.00", CobolPicture.money(new BigDecimal("1000.00")));
        assertEquals("+           .00", CobolPicture.money(new BigDecimal("0.00")));
        assertEquals("+123,456,789.99", CobolPicture.money(new BigDecimal("123456789.99")));
        assertEquals(15, CobolPicture.money(new BigDecimal("194.00")).length());
    }

    @Test
    @DisplayName("FR-16 — the fixed sign shows '-' for a negative balance (the account fields are S9(10)V99)")
    void moneyPictureSign() {
        assertEquals("-         12.34", CobolPicture.money(new BigDecimal("-12.34")));
        assertEquals("-      1,000.00", CobolPicture.money(new BigDecimal("-1000.00")));
    }

    @Test
    @DisplayName("FR-16 — a value wider than the nine integer positions loses its high-order digits, as the MOVE does")
    void moneyPictureTruncatesLikeMove() {
        assertEquals("+234,567,890.12", CobolPicture.money(new BigDecimal("1234567890.12")));
    }

    @Test
    @DisplayName("FR-19 — STRING CUST-SSN(1:3) '-' (4:2) '-' (6:4) over PIC 9(09) (cbl:496-504)")
    void ssnPicture() {
        assertEquals("980-16-1210", CobolPicture.ssn(980161210L));
        assertEquals("020-97-3888", CobolPicture.ssn(20973888L), "PIC 9(09) keeps the leading zero");
        assertEquals("", CobolPicture.ssn(null));
    }

    @Test
    @DisplayName("FR-18 / FR-19 — PIC 9(n) keys are zero-padded to the screen width")
    void digitPictures() {
        assertEquals("00000000027", CobolPicture.digits(27L, 11));
        assertEquals("000000027", CobolPicture.digits(27L, 9));
        assertEquals("99999999999", CobolPicture.digits(99999999999L, 11));
    }

    @Test
    @DisplayName("Q-10 — the X(10) date fields carry the stored value as ISO text (D-0037)")
    void datePicture() {
        assertEquals("2012-09-30", CobolPicture.date(LocalDate.of(2012, 9, 30)));
        assertEquals("", CobolPicture.date(null));
    }

    @Test
    @DisplayName("DV-05 — X(n) display values keep their content and drop only the COBOL pad blanks")
    void textPicture() {
        assertEquals("(935)027-1145", CobolPicture.text("(935)027-1145  "));
        assertEquals("07923-8822", CobolPicture.text("07923-8822"));
        assertEquals("", CobolPicture.text(null));
    }
}
