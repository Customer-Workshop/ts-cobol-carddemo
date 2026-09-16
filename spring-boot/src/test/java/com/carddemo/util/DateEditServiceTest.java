package com.carddemo.util;

import com.carddemo.util.DateEditService.DateEditRequest;
import com.carddemo.util.DateEditService.DateEditResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Truth table for the COBDATFT assembler port (app/asm/COBDATFT.asm).
 * The routine compares outType only against the one "wrong" code per input
 * type, always returns R15=0, and signals failure solely through the error
 * field — preserved branch-for-branch here (S17-FR-01).
 */
class DateEditServiceTest {

    private final DateEditService service = new DateEditService();

    @Test
    void typeOneToOneInsertsDashes() {
        DateEditResult result = service.edit(new DateEditRequest('1', "20250115", '1'));
        assertNull(result.errorMessage());
        assertEquals("2025-01-15" + " ".repeat(10), result.outDate());
        assertEquals("2025-01-15", result.date());
    }

    @Test
    void typeOneWithDashAlreadyPresentIsInvalid() {
        // COBDATFT asm line ~36: input byte 5 = '-' means already reformatted
        DateEditResult result = service.edit(new DateEditRequest('1', "2025-0115", '1'));
        assertEquals(DateEditService.INVALID_INPUT, result.errorMessage());
        assertEquals(" ".repeat(20), result.outDate());
    }

    @Test
    void typeOneRequestedAsTwoIsInvalid() {
        DateEditResult result = service.edit(new DateEditRequest('1', "20250115", '2'));
        assertEquals(DateEditService.INVALID_INPUT, result.errorMessage());
        assertEquals(" ".repeat(20), result.outDate());
    }

    @Test
    void typeTwoToTwoStripsDashes() {
        DateEditResult result = service.edit(new DateEditRequest('2', "2025-01-15", '2'));
        assertNull(result.errorMessage());
        assertEquals("20250115" + " ".repeat(12), result.outDate());
        assertEquals("20250115", result.date());
    }

    @Test
    void typeTwoRequestedAsOneIsInvalid() {
        DateEditResult result = service.edit(new DateEditRequest('2', "2025-01-15", '1'));
        assertEquals(DateEditService.INVALID_INPUT, result.errorMessage());
        assertEquals(" ".repeat(20), result.outDate());
    }

    @Test
    void typeTwoWithoutDashesStillProducesOutput() {
        // The dash-presence check on the '2' path is commented out in the asm
        // (lines 47-48), so undashed input is still rewritten.
        DateEditResult result = service.edit(new DateEditRequest('2', "20250115", '2'));
        assertNull(result.errorMessage());
        assertEquals("202511" + " ".repeat(14), result.outDate());
    }

    @Test
    void unrecognizedOutTypeStillProducesOutput() {
        // asm only rejects the one "wrong" code per path: '1'->'3' and '2'->'3'
        // are not errors (deviation flagged vs the "else INVALID INPUT"
        // shorthand in the wave brief).
        assertNull(service.edit(new DateEditRequest('1', "20250115", '3')).errorMessage());
        assertNull(service.edit(new DateEditRequest('2', "2025-01-15", '3')).errorMessage());
    }

    @Test
    void unrecognizedInTypeIsInvalid() {
        DateEditResult result = service.edit(new DateEditRequest('9', "20250115", '9'));
        assertEquals(DateEditService.INVALID_INPUT, result.errorMessage());
        assertEquals(" ".repeat(20), result.outDate());
    }

    @Test
    void inputFieldIsPaddedAndTruncatedToTwentyBytes() {
        assertNull(service.edit(new DateEditRequest('1', "20250115EXTRA-CHARS-HERE", '1'))
                .errorMessage());
        // null/blank input is space-padded: no error, output built from spaces
        DateEditResult blank = service.edit(new DateEditRequest('1', null, '1'));
        assertNull(blank.errorMessage());
        assertEquals("    -  -" + " ".repeat(12), blank.outDate());
    }
}
