package com.carddemo.service;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CSUTLDTC port (S-09 shared utility): FR-S09-16/31/32 rows, derived from
 * CSUTLDTC.cbl and its program FR doc — the CEEDAYS classification is an
 * emulation per S09_functional_requirement.md §11 A-1, so these tests pin
 * the contract the callers rely on, including the 80-byte LS-RESULT layout
 * (CSUTLDTC.cbl:47-57) and severity = RETURN-CODE (:97-98).
 */
class DateValidationServiceTest {

    private static final String MASK = "YYYY-MM-DD";

    private final DateValidationService service = new DateValidationService();

    @Test
    void validDateReturnsSeverityZeroAndValidFlag_frS0931() {
        DateValidationResult result = service.validate("2024-01-15", MASK);

        assertThat(result.severity()).isEqualTo("0000");
        assertThat(result.messageNumber()).isEqualTo("0000");
        assertThat(result.verdict()).isEqualTo("Date is valid  ");
        assertThat(result.valid()).isTrue();
        assertThat(result.returnCode()).isZero();
        assertThat(result.parsed()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(result.resultText()).isEqualTo(
                "0000" + "Mesg Code: " + "0000" + " " + "Date is valid  "
                        + " " + "TstDate: " + "2024-01-15" + " "
                        + "Mask used:" + "YYYY-MM-DD" + "    ");
        assertThat(result.resultText()).hasSize(80);
    }

    @Test
    void dayOutsideMonthReturns2508_frS0931() {
        DateValidationResult result = service.validate("2024-02-30", MASK);

        assertThat(result.severity()).isEqualTo("0003");
        assertThat(result.messageNumber()).isEqualTo("2508");
        assertThat(result.verdict()).isEqualTo("Datevalue error");
        assertThat(result.valid()).isFalse();
        assertThat(result.returnCode()).isEqualTo(3);
        assertThat(result.parsed()).isNull();
        assertThat(result.resultText()).isEqualTo(
                "0003" + "Mesg Code: " + "2508" + " " + "Datevalue error"
                        + " " + "TstDate: " + "2024-02-30" + " "
                        + "Mask used:" + "YYYY-MM-DD" + "    ");
    }

    @Test
    void monthOutsideRangeReturns2517_frS0931() {
        DateValidationResult result = service.validate("2024-13-01", MASK);

        assertThat(result.severity()).isEqualTo("0003");
        assertThat(result.messageNumber()).isEqualTo("2517");
        assertThat(result.verdict()).isEqualTo("Invalid month  ");
        assertThat(result.valid()).isFalse();
    }

    @Test
    void monthZeroIsAMonthErrorNotADayError_frS0931() {
        // A-1 order: the month edit fires before the day and range edits.
        assertThat(service.validate("1500-00-45", MASK).messageNumber()).isEqualTo("2517");
    }

    @Test
    void dayZeroAndDayOverflowReturn2508_frS0931() {
        assertThat(service.validate("2024-01-00", MASK).messageNumber()).isEqualTo("2508");
        assertThat(service.validate("2024-01-32", MASK).messageNumber()).isEqualTo("2508");
        // February honours the leap rules.
        assertThat(service.validate("2023-02-29", MASK).messageNumber()).isEqualTo("2508");
        assertThat(service.validate("2024-02-29", MASK).severity()).isEqualTo("0000");
    }

    @Test
    void preLillianDateReturns2513WithParsedDate_frS0932() {
        DateValidationResult result = service.validate("1500-01-01", MASK);

        assertThat(result.severity()).isEqualTo("0003");
        assertThat(result.messageNumber()).isEqualTo("2513");
        assertThat(result.verdict()).isEqualTo("Unsupp. Range  ");
        assertThat(result.valid()).isFalse();
        // The caller exempts 2513, so the date must materialise (D-2).
        assertThat(result.parsed()).isEqualTo(LocalDate.of(1500, 1, 1));
    }

    @Test
    void lillianBoundaryIsInclusive_frS0932() {
        assertThat(service.validate("1582-10-14", MASK).messageNumber()).isEqualTo("2513");
        assertThat(service.validate("1582-10-15", MASK).severity()).isEqualTo("0000");
    }

    @Test
    void yearZeroReturns2513WithNoParsedDate_frS0932() {
        DateValidationResult result = service.validate("0000-01-01", MASK);

        assertThat(result.messageNumber()).isEqualTo("2513");
        // Year 0000 clears the CEEDAYS edits in the source but cannot be
        // represented in the target (D-2): callers see it without a date.
        assertThat(result.parsed()).isNull();
    }

    @Test
    void nonDigitWhereDigitExpectedReturns2520_frS0931() {
        DateValidationResult result = service.validate("202a-01-15", MASK);

        assertThat(result.messageNumber()).isEqualTo("2520");
        assertThat(result.verdict()).isEqualTo("Nonnumeric data");
    }

    @Test
    void mismatchedSeparatorReturns2520_frS0931() {
        assertThat(service.validate("2024/01/15", MASK).messageNumber()).isEqualTo("2520");
        assertThat(service.validate("2024-1 -15", MASK).messageNumber()).isEqualTo("2520");
    }

    @Test
    void dateShorterThanMaskReturns2507_frS0931() {
        DateValidationResult result = service.validate("2024-01-1", MASK);

        assertThat(result.messageNumber()).isEqualTo("2507");
        assertThat(result.verdict()).isEqualTo("Insufficient   ");
        // A separator-free date is short before it is a pattern mismatch.
        assertThat(service.validate("20240115", MASK).messageNumber()).isEqualTo("2507");
    }

    @Test
    void blankOrNullDateReturns2507_frS0931() {
        assertThat(service.validate("", MASK).messageNumber()).isEqualTo("2507");
        assertThat(service.validate(null, MASK).messageNumber()).isEqualTo("2507");
    }

    @Test
    void unsupportedMaskTokenReturns2518_frS0931() {
        DateValidationResult result = service.validate("2024-01-15", "YY-MM-DD");

        assertThat(result.messageNumber()).isEqualTo("2518");
        assertThat(result.verdict()).isEqualTo("Bad Pic String ");
        assertThat(service.validate("2024-01-15", "YYYY-QQ-DD").messageNumber())
                .isEqualTo("2518");
    }

    @Test
    void reorderedMaskTokensAreHonoured_frS0931() {
        DateValidationResult result = service.validate("15/01/2024", "DD/MM/YYYY");

        assertThat(result.severity()).isEqualTo("0000");
        assertThat(result.parsed()).isEqualTo(LocalDate.of(2024, 1, 15));
    }

    @Test
    void errorResultTextKeepsTheEightyByteLayout_frS0931() {
        DateValidationResult result = service.validate("1500-01-01", MASK);

        assertThat(result.resultText()).hasSize(80).isEqualTo(
                "0003" + "Mesg Code: " + "2513" + " " + "Unsupp. Range  "
                        + " " + "TstDate: " + "1500-01-01" + " "
                        + "Mask used:" + "YYYY-MM-DD" + "    ");
    }
}
