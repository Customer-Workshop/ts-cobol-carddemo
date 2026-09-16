package com.carddemo.batch;

import org.junit.jupiter.api.Test;
import org.springframework.batch.repeat.RepeatStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FR-S14-14 — COBSWAIT → MVSWAIT parity (COBSWAIT.cbl:36-38; WAITSTEP.jcl:7-8).
 */
class WaitStepTaskletTest {

    @Test
    void sleepsForTheCentisecondCount() throws Exception {
        // 30 centiseconds = 300 ms; FR-S14-14 with a testable duration.
        long start = System.nanoTime();
        RepeatStatus status = new WaitStepTasklet(30).execute(null, null);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        assertEquals(RepeatStatus.FINISHED, status);
        assertTrue(elapsedMillis >= 300, "elapsed " + elapsedMillis + "ms < 300ms");
    }

    @Test
    void zeroCentisecondsReturnsImmediately() throws Exception {
        long start = System.nanoTime();
        RepeatStatus status = new WaitStepTasklet(0).execute(null, null);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        assertEquals(RepeatStatus.FINISHED, status);
        assertTrue(elapsedMillis < 100, "zero wait took " + elapsedMillis + "ms");
    }

    @Test
    void sysinCardParsingKeepsFirst8BytesOfLeadingDigits() {
        // COBSWAIT ACCEPTs an 8-byte X(8) card into 9(8) COMP (:36-37).
        assertEquals(3600, WaitStepTasklet.centiseconds("00003600"));
        assertEquals(3600, WaitStepTasklet.centiseconds("00003600      VALUE IN CENTISECONDS"));
        assertEquals(12, WaitStepTasklet.centiseconds("00000012"));
    }

    @Test
    void blankOrGarbageParmLandsOnZero() {
        // COBSWAIT-04: blanks/garbage MOVE to 0 — near-instant return.
        assertEquals(0, WaitStepTasklet.centiseconds(null));
        assertEquals(0, WaitStepTasklet.centiseconds(""));
        assertEquals(0, WaitStepTasklet.centiseconds("        "));
        assertEquals(0, WaitStepTasklet.centiseconds("NOTAPARM"));
    }

    @Test
    void interruptionFailsTheStep() {
        // FR-S14-14 edge: MVSWAIT has no feedback channel; interruption on target
        // surfaces as a step failure, never a silent early success.
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class,
                    () -> new WaitStepTasklet(360000).execute(null, null));
        } finally {
            Thread.interrupted();
        }
    }
}
