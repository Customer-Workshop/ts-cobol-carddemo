package com.carddemo.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CSUTLDTC port — expectations derived from app/cbl/CSUTLDTC.cbl and
 * functional/CardDemo/programs/CSUTLDTC_functional_requirement.md (FR-23, FR-24, §5.4, DV-04), never
 * from the implementation. Test names carry the FR id; "_INFERRED" marks CEEDAYS feedback mappings that
 * cannot be verified off-host (B-0015, D-0028).
 */
class DateValidationServiceTest {

    private final DateValidationService service = new DateValidationService();

    private DateValidationResult validate(String date, String mask) {
        return service.validate(new DateValidationRequest(date, mask));
    }

    // ---- FR-23 valid branch -------------------------------------------------------------------------

    @Test
    @DisplayName("FR-23 / AC-DTC-01: valid date -> 0000 'Date is valid', RC 0 (CSUTLDTC.cbl:88-100, :105-130)")
    void fr23_validDate_returnsSeverityZeroAndFullLegacyLayout() {
        DateValidationResult result = validate("20240229", "YYYYMMDD");

        assertEquals(0, result.severity());
        assertEquals("0000", result.messageNumber());
        assertEquals("Date is valid  ", result.reasonText());
        assertEquals("0000Mesg Code: 0000 Date is valid   TstDate: 20240229   Mask used:YYYYMMDD      ",
                result.formatted80());
        assertEquals(80, result.formatted80().length());
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("FR-23 / AC-DTC-02: separator mask YYYY-MM-DD accepted and echoed at bytes 67-76 (CSUTLDTC.cbl:109-113)")
    void fr23_validDateWithSeparatorMask_echoesMask() {
        DateValidationResult result = validate("2024-02-29", "YYYY-MM-DD");

        assertEquals(0, result.severity());
        assertEquals("0000", result.messageNumber());
        assertEquals("YYYY-MM-DD", result.formatted80().substring(66, 76));
    }

    @Test
    @DisplayName("FR-23: linkage is X(10) space-padded — trailing spaces on date and mask are not data (CSUTLDTC.cbl:84-85)")
    void fr23_spacePaddedLinkage_isValid() {
        DateValidationResult result = validate("20240229  ", "YYYYMMDD  ");

        assertEquals(0, result.severity());
        assertEquals("0000", result.messageNumber());
        assertEquals("20240229  ", result.formatted80().substring(45, 55));
    }

    // ---- FR-24 invalid branch, §5.4 result catalogue ---------------------------------------------------

    @Test
    @DisplayName("FR-24 / AC-DTC-03: impossible calendar date -> severity 0003 with a catalogue code (CSUTLDTC.cbl:60-73, :122-149)")
    void fr24_impossibleCalendarDate_isResultNotException() {
        DateValidationResult result = validate("20240230", "YYYYMMDD");

        assertEquals(3, result.severity());
        assertFalse(result.isValid());
        assertEquals("0003", result.formatted80().substring(0, 4));
        assertTrue(java.util.Set.of("2507", "2508", "2509", "2513", "2517", "2518", "2520", "2521")
                .contains(result.messageNumber()));
        assertEquals(15, result.reasonText().length());
    }

    @Test
    @DisplayName("FR-24 / §5.4 2508 'Datevalue error' for day-of-month impossible — INFERRED mapping (CSUTLDTC.cbl:132-133)")
    void fr24_dayOfMonthImpossible_2508_INFERRED() {
        DateValidationResult result = validate("20230229", "YYYYMMDD");

        assertEquals("2508", result.messageNumber());
        assertEquals("Datevalue error", result.reasonText());
    }

    @Test
    @DisplayName("FR-24 / AC-DTC-04 / §5.4 2513 'Unsupp. Range' before the Lilian epoch — CONTRACTUAL (CSUTLDTC.cbl:136-137)")
    void fr24_dateBeforeLilianEpoch_2513_contractual() {
        DateValidationResult result = validate("15000101", "YYYYMMDD");

        assertEquals(3, result.severity());
        assertEquals("2513", result.messageNumber());
        assertEquals("Unsupp. Range  ", result.reasonText());
    }

    @Test
    @DisplayName("FR-24 / §5.4 2513: 14 Oct 1582 rejected, 15 Oct 1582 accepted — Lilian day 1 (A-DTC-3, INFERRED bound)")
    void fr24_lilianEpochBoundary_2513_INFERRED() {
        assertEquals("2513", validate("15821014", "YYYYMMDD").messageNumber());
        assertEquals("0000", validate("15821015", "YYYYMMDD").messageNumber());
    }

    @Test
    @DisplayName("FR-24 / AC-DTC-05 / §5.4 2517 'Invalid month' for month 13 — INFERRED (CSUTLDTC.cbl:138-139)")
    void fr24_monthThirteen_2517_INFERRED() {
        DateValidationResult result = validate("20241301", "YYYYMMDD");

        assertEquals("2517", result.messageNumber());
        assertEquals("Invalid month  ", result.reasonText());
    }

    @Test
    @DisplayName("FR-24 / AC-DTC-06 / §5.4 2520 'Nonnumeric data' for letters in a digit position — INFERRED (CSUTLDTC.cbl:142-143)")
    void fr24_nonDigitInput_2520_INFERRED() {
        DateValidationResult result = validate("2024AB01", "YYYYMMDD");

        assertEquals("2520", result.messageNumber());
        assertEquals("Nonnumeric data", result.reasonText());
    }

    @Test
    @DisplayName("FR-24 / AC-DTC-07 / §5.4 2507 'Insufficient' for blank input — INFERRED (CSUTLDTC.cbl:130-131)")
    void fr24_blankDate_2507_INFERRED() {
        DateValidationResult result = validate("          ", "YYYYMMDD");

        assertEquals("2507", result.messageNumber());
        assertEquals("Insufficient   ", result.reasonText());
    }

    @Test
    @DisplayName("FR-24 / §5.4 2507 'Insufficient' when the date is shorter than the picture — INFERRED")
    void fr24_shortDate_2507_INFERRED() {
        assertEquals("2507", validate("2024", "YYYYMMDD").messageNumber());
    }

    @Test
    @DisplayName("FR-24 / §5.4 2518 'Bad Pic String' for a mask that is not a picture — INFERRED (CSUTLDTC.cbl:140-141)")
    void fr24_badPictureMask_2518_INFERRED() {
        DateValidationResult result = validate("20240229", "ZZZZ");

        assertEquals("2518", result.messageNumber());
        assertEquals("Bad Pic String ", result.reasonText());
    }

    @Test
    @DisplayName("FR-24 / §5.4 2509 'Invalid Era' and 2521 'YearInEra is 0' are unreachable with Gregorian masks (A-DTC-2, INFERRED)")
    void fr24_eraCodes_unreachable_INFERRED() {
        for (String date : new String[] {"00000101", "00010101", "99991231"}) {
            String code = validate(date, "YYYYMMDD").messageNumber();
            assertFalse(code.equals("2509") || code.equals("2521"), date + " produced an era code");
        }
        assertEquals("2513", validate("00000101", "YYYYMMDD").messageNumber());
        assertEquals("0000", validate("99991231", "YYYYMMDD").messageNumber());
    }

    @Test
    @DisplayName("FR-24 / AC-DTC-08: unclassified input -> WHEN OTHER 'Date is invalid', severity 3, no exception (CSUTLDTC.cbl:146-147)")
    void fr24_unclassifiedInput_fallsBackToDateIsInvalid() {
        DateValidationResult result = validate("2024022900", "YYYYMMDD");

        assertEquals(3, result.severity());
        assertEquals("Date is invalid", result.reasonText());
        assertEquals(DateValidationService.UNCLASSIFIED_MESSAGE_NUMBER, result.messageNumber());
    }

    // ---- §5.4 / DTC-03 output layout ---------------------------------------------------------------------

    @Test
    @DisplayName("DTC-03 / AC-DTC-09: WS-MESSAGE layout is exactly 80 bytes with the fixed labels (CSUTLDTC.cbl:42-57; CSUTLDWY.cpy:60-85)")
    void dtc03_layoutIsExactly80BytesWithLegacyLabels() {
        for (DateValidationResult result : new DateValidationResult[] {
                validate("20240229", "YYYYMMDD"),
                validate("20241301", "YYYYMMDD"),
                validate("20240229", "ZZZZ"),
                validate("          ", "YYYYMMDD")}) {
            String f = result.formatted80();
            assertEquals(80, f.length());
            assertEquals(String.format("%04d", result.severity()), f.substring(0, 4));
            assertEquals("Mesg Code: ", f.substring(4, 15));
            assertEquals(result.messageNumber(), f.substring(15, 19));
            assertEquals(" ", f.substring(19, 20));
            assertEquals(result.reasonText(), f.substring(20, 35));
            assertEquals(" ", f.substring(35, 36));
            assertEquals("TstDate: ", f.substring(36, 45));
            assertEquals(" ", f.substring(55, 56));
            assertEquals("Mask used:", f.substring(56, 66));
            assertEquals("    ", f.substring(76, 80));
        }
    }

    @Test
    @DisplayName("DV-04 / Q-07 / AC-DTC-10: bytes 46-55 hold the date AS PASSED (legacy overwrites WS-DATE with the VSTRING group, CSUTLDTC.cbl:108 vs :122)")
    void dv04_tstDateSegmentHoldsIntendedDateNotVstringBytes() {
        // Deliberate deviation DV-04: the COBOL result carries 2 binary length bytes + the first 8
        // characters here; the target writes the intended 10-character value from :108.
        DateValidationResult valid = validate("2024-02-29", "YYYY-MM-DD");
        assertEquals("2024-02-29", valid.formatted80().substring(45, 55));

        DateValidationResult padded = validate("20240229", "YYYYMMDD");
        assertEquals("20240229  ", padded.formatted80().substring(45, 55));

        DateValidationResult invalid = validate("20241301", "YYYYMMDD");
        assertEquals("20241301  ", invalid.formatted80().substring(45, 55));
    }

    // ---- B-0014 programming errors and purity ------------------------------------------------------------

    @Test
    @DisplayName("B-0014 / AC-DTC-11: null date or mask is a programming error -> IllegalArgumentException")
    void b0014_nullArguments_throwIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> validate(null, "YYYYMMDD"));
        assertThrows(IllegalArgumentException.class, () -> validate("20240229", null));
        assertThrows(IllegalArgumentException.class, () -> service.validate(null));
    }

    @Test
    @DisplayName("B-0014 / FR §6 edge: values longer than X(10) are a caller error like null (wave-1 decision, INFERRED policy)")
    void b0014_overLongLinkage_throwsIllegalArgument_INFERRED() {
        assertThrows(IllegalArgumentException.class, () -> validate("2024-02-29T", "YYYY-MM-DD"));
        assertThrows(IllegalArgumentException.class, () -> validate("2024-02-29", "YYYY-MM-DDX"));
    }

    @Test
    @DisplayName("B-0014: pure function — repeated calls with the same input give identical results, no state")
    void b0014_repeatedCallsArePure() {
        DateValidationResult first = validate("20240229", "YYYYMMDD");
        validate("garbage!!", "YYYYMMDD");
        DateValidationResult second = validate("20240229", "YYYYMMDD");

        assertEquals(first, second);
    }
}
